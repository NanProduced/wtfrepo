package com.wtfrepo.backend.notifications.application;

import com.wtfrepo.backend.notifications.application.support.NotificationsConstants;
import com.wtfrepo.backend.notifications.application.support.NotificationsExceptions;
import com.wtfrepo.backend.notifications.domain.BroadcastStatus;
import com.wtfrepo.backend.notifications.domain.NotificationStatus;
import com.wtfrepo.backend.notifications.domain.NotificationType;
import com.wtfrepo.backend.notifications.infra.persistence.entity.SystemBroadcastJpaEntity;
import com.wtfrepo.backend.notifications.infra.persistence.entity.UserBroadcastCheckpointJpaEntity;
import com.wtfrepo.backend.notifications.infra.persistence.entity.UserNotificationJpaEntity;
import com.wtfrepo.backend.notifications.infra.persistence.repository.SystemBroadcastJpaRepository;
import com.wtfrepo.backend.notifications.infra.persistence.repository.UserBroadcastCheckpointJpaRepository;
import com.wtfrepo.backend.notifications.infra.persistence.repository.UserNotificationJpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** M06 notifications application service aligned with contract v0.1 public scope. */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final String STATUS_ALL = "ALL";
  private static final String BROADCAST_DEDUPE_PREFIX = "broadcast_";
  private static final String BROADCAST_FALLBACK_URL = "/";

  private final UserNotificationJpaRepository notificationRepository;
  private final SystemBroadcastJpaRepository systemBroadcastRepository;
  private final UserBroadcastCheckpointJpaRepository broadcastCheckpointRepository;
  private final StringRedisTemplate redisTemplate;
  private final NotificationsPolicyProperties policyProperties;
  private final NotificationSsePublisher ssePublisher;

  public NotificationService(
      UserNotificationJpaRepository notificationRepository,
      SystemBroadcastJpaRepository systemBroadcastRepository,
      UserBroadcastCheckpointJpaRepository broadcastCheckpointRepository,
      StringRedisTemplate redisTemplate,
      NotificationsPolicyProperties policyProperties,
      NotificationSsePublisher ssePublisher) {
    this.notificationRepository = notificationRepository;
    this.systemBroadcastRepository = systemBroadcastRepository;
    this.broadcastCheckpointRepository = broadcastCheckpointRepository;
    this.redisTemplate = redisTemplate;
    this.policyProperties = policyProperties;
    this.ssePublisher = ssePublisher;
  }

  @Transactional
  public ListResult list(String userId, ListQuery query) {
    String normalizedUserId = requireText(userId, "userId");
    NotificationStatusScope statusScope = normalizeStatusScope(query.status());
    NotificationType typeFilter = normalizeType(query.type());
    int limit = normalizeListLimit(query.limit());
    CursorAnchor cursorAnchor = resolveCursorAnchor(normalizedUserId, query.cursor());

    BroadcastBackfillResult backfillResult = backfillBroadcasts(normalizedUserId);
    if (backfillResult.advanced()) {
      refreshUnreadCountCache(normalizedUserId);
    }

    List<UserNotificationJpaEntity> entities =
        notificationRepository.findPageAfterCursor(
            normalizedUserId,
            statusScope.statuses(),
            typeFilter,
            cursorAnchor == null ? null : cursorAnchor.createdAt(),
            cursorAnchor == null ? null : cursorAnchor.id(),
            PageRequest.of(0, limit + 1));

    boolean hasMore = entities.size() > limit;
    List<UserNotificationJpaEntity> pageWindow = hasMore ? entities.subList(0, limit) : entities;
    List<ListItem> items = pageWindow.stream().map(this::toListItem).toList();
    String nextCursor =
        hasMore && !pageWindow.isEmpty()
            ? pageWindow.get(pageWindow.size() - 1).getNotificationUid()
            : null;

    return new ListResult(items, nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public UnreadCountResult unreadCount(String userId) {
    String normalizedUserId = requireText(userId, "userId");
    String key = unreadKey(normalizedUserId);

    Long cachedValue = readCachedUnreadCount(key);
    if (cachedValue != null) {
      return new UnreadCountResult(cachedValue);
    }

    long unread =
        notificationRepository.countByReceiverUserIdAndStatus(
            normalizedUserId, NotificationStatus.UNREAD);
    UserBroadcastCheckpointJpaEntity checkpoint = resolveCheckpoint(normalizedUserId);
    long pendingBroadcasts = countPendingBroadcasts(checkpoint);
    long total = unread + pendingBroadcasts;
    writeUnreadCountCache(key, total);
    return new UnreadCountResult(total);
  }

  @Transactional
  public MarkReadResult markRead(String userId, String notificationUid) {
    String normalizedUserId = requireText(userId, "userId");
    String normalizedUid = requireText(notificationUid, "notificationUid");

    UserNotificationJpaEntity entity =
        notificationRepository
            .findByNotificationUid(normalizedUid)
            .orElseThrow(
                () ->
                    NotificationsExceptions.notFound(
                        NotificationsConstants.Message.NOTIFICATION_NOT_FOUND));
    if (!Objects.equals(entity.getReceiverUserId(), normalizedUserId)) {
      throw NotificationsExceptions.forbidden(
          NotificationsConstants.Message.NOTIFICATION_FORBIDDEN);
    }

    boolean changed = entity.markRead();
    notificationRepository.save(entity);
    if (changed) {
      decrementUnreadCountCache(unreadKey(normalizedUserId));
    }

    return new MarkReadResult(
        entity.getNotificationUid(), entity.getStatus(), entity.getReadAt());
  }

  @Transactional
  public MarkAllReadResult markAllRead(String userId) {
    String normalizedUserId = requireText(userId, "userId");
    Instant now = Instant.now();
    int updated =
        notificationRepository.markAllRead(
            normalizedUserId, NotificationStatus.UNREAD, NotificationStatus.READ, now, now);
    UserBroadcastCheckpointJpaEntity checkpoint = resolveCheckpoint(normalizedUserId);
    long pendingBroadcasts = countPendingBroadcasts(checkpoint);
    writeUnreadCountCache(unreadKey(normalizedUserId), pendingBroadcasts);
    return new MarkAllReadResult(updated);
  }

  private CursorAnchor resolveCursorAnchor(String userId, String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    String normalizedCursor = cursor.trim();
    UserNotificationJpaEntity anchor =
        notificationRepository
            .findByNotificationUid(normalizedCursor)
            .orElseThrow(
                () ->
                    NotificationsExceptions.invalidCursor(
                        NotificationsConstants.Message.INVALID_CURSOR));
    if (!Objects.equals(anchor.getReceiverUserId(), userId)) {
      throw NotificationsExceptions.invalidCursor(
          NotificationsConstants.Message.INVALID_CURSOR);
    }
    return new CursorAnchor(anchor.getCreatedAt(), anchor.getId());
  }

  private ListItem toListItem(UserNotificationJpaEntity entity) {
    return new ListItem(
        entity.getNotificationUid(),
        entity.getType().name(),
        entity.getTitle(),
        entity.getBody(),
        entity.getActorNickname(),
        null,
        entity.getAggregateCount(),
        entity.getTargetUrl(),
        entity.getFallbackUrl(),
        entity.getStatus().name(),
        entity.getCreatedAt());
  }

  private NotificationStatusScope normalizeStatusScope(String status) {
    if (!StringUtils.hasText(status)) {
      return NotificationStatusScope.all();
    }

    String normalized = status.trim().toUpperCase(Locale.ROOT);
    if (STATUS_ALL.equals(normalized)) {
      return NotificationStatusScope.all();
    }

    try {
      NotificationStatus resolved = NotificationStatus.valueOf(normalized);
      return NotificationStatusScope.single(resolved);
    } catch (IllegalArgumentException ex) {
      throw NotificationsExceptions.invalidStatus(
          NotificationsConstants.Message.INVALID_STATUS);
    }
  }

  private NotificationType normalizeType(String type) {
    if (!StringUtils.hasText(type)) {
      return null;
    }
    try {
      return NotificationType.valueOf(type.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw NotificationsExceptions.invalidType(NotificationsConstants.Message.INVALID_TYPE);
    }
  }

  private int normalizeListLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return policyProperties.getListDefaultLimit();
    }
    return Math.min(limit, policyProperties.getListMaxLimit());
  }

  private BroadcastBackfillResult backfillBroadcasts(String userId) {
    UserBroadcastCheckpointJpaEntity checkpoint = resolveCheckpoint(userId);
    Instant lastBroadcastAt = checkpoint == null ? null : checkpoint.getLastBroadcastAt();
    Long lastBroadcastId = checkpoint == null ? null : checkpoint.getLastBroadcastId();
    int batchSize = Math.max(1, policyProperties.getBroadcastBatchSize());

    int insertedCount = 0;
    boolean advanced = false;
    Instant maxCreatedAt = lastBroadcastAt;

    while (true) {
      List<SystemBroadcastJpaEntity> broadcasts =
          systemBroadcastRepository.findPendingBroadcasts(
              BroadcastStatus.COMPLETED,
              lastBroadcastAt,
              lastBroadcastId == null ? 0L : lastBroadcastId,
              PageRequest.of(0, batchSize));
      if (broadcasts.isEmpty()) {
        break;
      }

      for (SystemBroadcastJpaEntity broadcast : broadcasts) {
        UserNotificationJpaEntity entity = toBroadcastNotification(userId, broadcast);
        try {
          notificationRepository.save(entity);
          insertedCount++;
          ssePublisher.publishPager(entity);
        } catch (DataIntegrityViolationException ex) {
          log.debug(
              "notify_broadcast_duplicate userId={} broadcastUid={}",
              userId,
              broadcast.getBroadcastUid());
        }
        maxCreatedAt = maxInstant(maxCreatedAt, broadcast.getCreatedAt());
        lastBroadcastId = broadcast.getId();
      }

      advanced = true;
      lastBroadcastAt = maxCreatedAt;
      if (broadcasts.size() < batchSize) {
        break;
      }
    }

    if (advanced && maxCreatedAt != null) {
      if (checkpoint == null) {
        checkpoint = UserBroadcastCheckpointJpaEntity.create(userId, maxCreatedAt, lastBroadcastId);
      } else {
        checkpoint.advanceTo(maxCreatedAt, lastBroadcastId);
      }
      broadcastCheckpointRepository.save(checkpoint);
    }

    return new BroadcastBackfillResult(insertedCount, advanced);
  }

  private UserNotificationJpaEntity toBroadcastNotification(
      String userId, SystemBroadcastJpaEntity broadcast) {
    String dedupeKey = BROADCAST_DEDUPE_PREFIX + broadcast.getBroadcastUid();
    return UserNotificationJpaEntity.create(
        userId,
        NotificationType.SYSTEM_BROADCAST,
        dedupeKey,
        broadcast.getTitle(),
        broadcast.getBody(),
        1,
        null,
        null,
        broadcast.getTargetUrl(),
        BROADCAST_FALLBACK_URL);
  }

  private long countPendingBroadcasts(UserBroadcastCheckpointJpaEntity checkpoint) {
    Instant lastBroadcastAt = checkpoint == null ? null : checkpoint.getLastBroadcastAt();
    Long lastBroadcastId = checkpoint == null ? null : checkpoint.getLastBroadcastId();
    try {
      return systemBroadcastRepository.countPendingBroadcasts(
          BroadcastStatus.COMPLETED,
          lastBroadcastAt,
          lastBroadcastId == null ? 0L : lastBroadcastId);
    } catch (RuntimeException ex) {
      String checkpointUserId = checkpoint == null ? null : checkpoint.getUserId();
      log.warn("notify_broadcast_pending_count_failed userId={}", checkpointUserId, ex);
      return 0;
    }
  }

  private UserBroadcastCheckpointJpaEntity resolveCheckpoint(String userId) {
    try {
      return broadcastCheckpointRepository.findById(userId).orElse(null);
    } catch (RuntimeException ex) {
      log.warn("notify_broadcast_checkpoint_read_failed userId={}", userId, ex);
      return null;
    }
  }

  private String unreadKey(String userId) {
    return policyProperties.getUnreadKeyPrefix() + userId;
  }

  private Long readCachedUnreadCount(String key) {
    try {
      String cached = redisTemplate.opsForValue().get(key);
      if (!StringUtils.hasText(cached)) {
        return null;
      }
      return Long.parseLong(cached.trim());
    } catch (RuntimeException ex) {
      log.warn("notification_unread_cache_read_failed key={}", key, ex);
      return null;
    }
  }

  private void writeUnreadCountCache(String key, long count) {
    try {
      if (policyProperties.getUnreadCacheTtl().isZero()
          || policyProperties.getUnreadCacheTtl().isNegative()) {
        redisTemplate.opsForValue().set(key, String.valueOf(count));
      } else {
        redisTemplate
            .opsForValue()
            .set(key, String.valueOf(count), policyProperties.getUnreadCacheTtl());
      }
    } catch (RuntimeException ex) {
      log.warn("notification_unread_cache_write_failed key={}", key, ex);
    }
  }

  private void decrementUnreadCountCache(String key) {
    try {
      Long result = redisTemplate.opsForValue().decrement(key);
      if (result != null && result < 0) {
        redisTemplate.opsForValue().set(key, "0");
      }
    } catch (RuntimeException ex) {
      log.warn("notification_unread_cache_decr_failed key={}", key, ex);
    }
  }

  private String requireText(String value, String field) {
    if (!StringUtils.hasText(value)) {
      throw NotificationsExceptions.validation(field + " is required");
    }
    return value.trim();
  }

  public record ListQuery(String status, String type, String cursor, Integer limit) {}

  public record ListResult(List<ListItem> items, String nextCursor, boolean hasMore) {}

  public record ListItem(
      String notificationUid,
      String type,
      String title,
      String body,
      String actorNickname,
      String actorAvatarUrl,
      int aggregateCount,
      String targetUrl,
      String fallbackUrl,
      String status,
      Instant createdAt) {}

  public record UnreadCountResult(long unreadCount) {}

  public record MarkReadResult(String notificationUid, NotificationStatus status, Instant readAt) {}

  public record MarkAllReadResult(int updatedCount) {}

  private record CursorAnchor(Instant createdAt, Long id) {}

  private record NotificationStatusScope(Collection<NotificationStatus> statuses, boolean allStatuses) {

    static NotificationStatusScope all() {
      return new NotificationStatusScope(List.of(NotificationStatus.UNREAD, NotificationStatus.READ), true);
    }

    static NotificationStatusScope single(NotificationStatus status) {
      return new NotificationStatusScope(List.of(status), false);
    }
  }

  private record BroadcastBackfillResult(int insertedCount, boolean advanced) {}

  private void refreshUnreadCountCache(String userId) {
    long count =
        notificationRepository.countByReceiverUserIdAndStatus(userId, NotificationStatus.UNREAD);
    writeUnreadCountCache(unreadKey(userId), count);
  }

  private Instant maxInstant(Instant left, Instant right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return right.isAfter(left) ? right : left;
  }
}
