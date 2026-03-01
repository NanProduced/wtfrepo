package com.wtfrepo.backend.notifications.application;

import com.wtfrepo.backend.auth.infra.persistence.entity.AuthUserJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import com.wtfrepo.backend.notifications.domain.NotificationType;
import com.wtfrepo.backend.notifications.infra.persistence.entity.UserNotificationJpaEntity;
import com.wtfrepo.backend.notifications.infra.persistence.repository.UserNotificationJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Notifications-side external outbox event consumer application service.
 *
 * <p>This service centralizes cross-module notification intake so handler classes remain thin.
 */
@Service
@ConditionalOnBean({EntityManagerFactory.class, UserNotificationJpaRepository.class})
public class NotificationOutboxEventConsumerService {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationOutboxEventConsumerService.class);

  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String SPECIMEN_PATH_PREFIX = "/specimen/";
  private static final String COMMENT_ANCHOR_PREFIX = "#comment-";
  private static final String DEFAULT_SPECIMEN_TITLE = "某标本";
  private static final String DEFAULT_ACTOR_NICKNAME = "有人";
  private static final String DEFAULT_ACHIEVEMENT_NAME = "隐藏成就";
  private static final String ACHIEVEMENT_TITLE = "你解锁了新成就";
  private static final String ACHIEVEMENT_DEDUPE_PREFIX = "achievement_";
  private static final String PROFILE_ACHIEVEMENTS_PREFIX = "/profile/achievements#";
  private static final String PROFILE_FALLBACK_URL = "/profile";
  private static final String AGG_KEY_PREFIX = "notify:agg:";
  private static final String AGG_KEY_SUFFIX = ":RESONATED";

  private final UserNotificationJpaRepository notificationRepository;
  private final AuthUserJpaRepository authUserRepository;
  private final SpecimenJpaRepository specimenRepository;
  private final StringRedisTemplate redisTemplate;
  private final NotificationsPolicyProperties policyProperties;
  private final NotificationSsePublisher ssePublisher;

  public NotificationOutboxEventConsumerService(
      UserNotificationJpaRepository notificationRepository,
      AuthUserJpaRepository authUserRepository,
      SpecimenJpaRepository specimenRepository,
      StringRedisTemplate redisTemplate,
      NotificationsPolicyProperties policyProperties,
      NotificationSsePublisher ssePublisher) {
    this.notificationRepository = notificationRepository;
    this.authUserRepository = authUserRepository;
    this.specimenRepository = specimenRepository;
    this.redisTemplate = redisTemplate;
    this.policyProperties = policyProperties;
    this.ssePublisher = ssePublisher;
  }

  @Transactional
  public void onCommentPublished(
      String eventId,
      String commentId,
      String specimenId,
      String authorUserId,
      String replyToUserId,
      String status,
      Instant occurredAt) {
    String normalizedStatus = normalize(status);
    if (!STATUS_ACTIVE.equalsIgnoreCase(normalizedStatus)) {
      log.debug("notify_comment_published_skip eventId={} reason=inactive_status status={}", eventId, status);
      return;
    }
    if (!StringUtils.hasText(commentId)
        || !StringUtils.hasText(specimenId)
        || !StringUtils.hasText(authorUserId)) {
      log.warn("notify_comment_published_skip eventId={} reason=missing_fields", eventId);
      return;
    }
    if (!StringUtils.hasText(replyToUserId)) {
      return;
    }

    String receiverUserId = replyToUserId.trim();
    String actorUserId = authorUserId.trim();
    if (Objects.equals(receiverUserId, actorUserId)) {
      return;
    }

    String dedupeKey = "reply_" + commentId.trim();
    if (existsByDedupe(receiverUserId, NotificationType.COMMENT_REPLIED, dedupeKey)) {
      return;
    }

    String actorNickname = resolveNickname(actorUserId);
    String specimenTitle = resolveSpecimenTitle(specimenId);
    String title = "有人回复了你的处方";
    String body = buildReplyBody(actorNickname, specimenTitle);
    String targetUrl = buildTargetUrl(specimenId, commentId);
    String fallbackUrl = buildFallbackUrl(specimenId);

    UserNotificationJpaEntity entity =
        UserNotificationJpaEntity.create(
            receiverUserId,
            NotificationType.COMMENT_REPLIED,
            dedupeKey,
            title,
            body,
            1,
            actorUserId,
            actorNickname,
            targetUrl,
            fallbackUrl);
    persistNotification(entity, receiverUserId);
  }

  @Transactional
  public void onCommentResonance(
      String eventId,
      String commentId,
      String specimenId,
      String resonatorUserId,
      String commentAuthorUserId,
      Instant occurredAt) {
    if (!StringUtils.hasText(commentId)
        || !StringUtils.hasText(specimenId)
        || !StringUtils.hasText(resonatorUserId)
        || !StringUtils.hasText(commentAuthorUserId)) {
      log.warn("notify_comment_resonance_skip eventId={} reason=missing_fields", eventId);
      return;
    }
    String receiverUserId = commentAuthorUserId.trim();
    String actorUserId = resonatorUserId.trim();
    if (Objects.equals(receiverUserId, actorUserId)) {
      return;
    }

    String aggregationKey = aggregationKey(receiverUserId, commentId);
    if (tryAggregateResonance(receiverUserId, aggregationKey)) {
      return;
    }

    String dedupeKey = "resonance_" + commentId.trim() + "_" + actorUserId;
    if (existsByDedupe(receiverUserId, NotificationType.COMMENT_RESONATED, dedupeKey)) {
      return;
    }

    String actorNickname = resolveNickname(actorUserId);
    String title = "有人复议了你的处方";
    String body = buildResonanceBodySingle(actorNickname);
    String targetUrl = buildTargetUrl(specimenId, commentId);
    String fallbackUrl = buildFallbackUrl(specimenId);

    UserNotificationJpaEntity entity =
        UserNotificationJpaEntity.create(
            receiverUserId,
            NotificationType.COMMENT_RESONATED,
            dedupeKey,
            title,
            body,
            1,
            actorUserId,
            actorNickname,
            targetUrl,
            fallbackUrl);

    UserNotificationJpaEntity saved = persistNotification(entity, receiverUserId);
    cacheAggregationAnchor(aggregationKey, saved.getId());
  }

  @Transactional
  public void onCommentMention(
      String eventId,
      String commentId,
      String specimenId,
      String authorUserId,
      List<String> mentionedUserIds,
      Instant occurredAt) {
    if (!StringUtils.hasText(commentId)
        || !StringUtils.hasText(specimenId)
        || !StringUtils.hasText(authorUserId)) {
      log.warn("notify_comment_mention_skip eventId={} reason=missing_fields", eventId);
      return;
    }
    if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
      return;
    }

    String actorUserId = authorUserId.trim();
    Set<String> uniqueMentions = new LinkedHashSet<>();
    for (String mentionedUserId : mentionedUserIds) {
      if (!StringUtils.hasText(mentionedUserId)) {
        continue;
      }
      String normalized = mentionedUserId.trim();
      if (Objects.equals(normalized, actorUserId)) {
        continue;
      }
      uniqueMentions.add(normalized);
    }
    if (uniqueMentions.isEmpty()) {
      return;
    }

    String actorNickname = resolveNickname(actorUserId);
    String specimenTitle = resolveSpecimenTitle(specimenId);
    String title = "有人在评论中提到了你";
    String body = buildMentionBody(actorNickname, specimenTitle);
    String targetUrl = buildTargetUrl(specimenId, commentId);
    String fallbackUrl = buildFallbackUrl(specimenId);

    for (String receiverUserId : uniqueMentions) {
      String dedupeKey = "mention_" + commentId.trim() + "_" + receiverUserId;
      if (existsByDedupe(receiverUserId, NotificationType.MENTIONED_IN_COMMENT, dedupeKey)) {
        continue;
      }
      UserNotificationJpaEntity entity =
          UserNotificationJpaEntity.create(
              receiverUserId,
              NotificationType.MENTIONED_IN_COMMENT,
              dedupeKey,
              title,
              body,
              1,
              actorUserId,
              actorNickname,
              targetUrl,
              fallbackUrl);
      persistNotification(entity, receiverUserId);
    }
  }

  @Transactional
  public void onAchievementUnlocked(
      String eventId,
      String userId,
      String achievementCode,
      String displayName,
      Integer rewardBug,
      Instant occurredAt) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(achievementCode)) {
      log.warn("notify_achievement_unlocked_skip eventId={} reason=missing_fields", eventId);
      return;
    }

    String receiverUserId = userId.trim();
    String normalizedAchievementCode = achievementCode.trim();
    String dedupeKey = ACHIEVEMENT_DEDUPE_PREFIX + normalizedAchievementCode;
    if (existsByDedupe(receiverUserId, NotificationType.ACHIEVEMENT_UNLOCKED, dedupeKey)) {
      return;
    }

    String body = buildAchievementUnlockedBody(displayName, rewardBug);
    UserNotificationJpaEntity entity =
        UserNotificationJpaEntity.create(
            receiverUserId,
            NotificationType.ACHIEVEMENT_UNLOCKED,
            dedupeKey,
            ACHIEVEMENT_TITLE,
            body,
            1,
            null,
            null,
            PROFILE_ACHIEVEMENTS_PREFIX + normalizedAchievementCode,
            PROFILE_FALLBACK_URL);
    persistNotification(entity, receiverUserId);
  }

  private boolean tryAggregateResonance(String receiverUserId, String aggregationKey) {
    String cached = readCacheValue(aggregationKey);
    if (!StringUtils.hasText(cached)) {
      return false;
    }
    Long notificationId = parseLong(cached);
    if (notificationId == null) {
      deleteCacheKey(aggregationKey);
      return false;
    }
    UserNotificationJpaEntity existing = notificationRepository.findById(notificationId).orElse(null);
    if (existing == null) {
      deleteCacheKey(aggregationKey);
      return false;
    }

    int nextCount = existing.getAggregateCount() + 1;
    existing.applyAggregation(nextCount, buildResonanceBodyAggregate(nextCount));
    notificationRepository.save(existing);
    ssePublisher.publishPager(existing);
    return true;
  }

  private UserNotificationJpaEntity persistNotification(
      UserNotificationJpaEntity entity, String receiverUserId) {
    try {
      UserNotificationJpaEntity saved = notificationRepository.save(entity);
      incrementUnreadCount(receiverUserId);
      ssePublisher.publishPager(saved);
      return saved;
    } catch (DataIntegrityViolationException ex) {
      log.warn(
          "notify_insert_duplicate receiverUserId={} type={} dedupeKey={}",
          receiverUserId,
          entity.getType(),
          entity.getDedupeKey());
      return entity;
    }
  }

  private boolean existsByDedupe(String receiverUserId, NotificationType type, String dedupeKey) {
    try {
      return notificationRepository.existsByReceiverUserIdAndTypeAndDedupeKey(
          receiverUserId, type, dedupeKey);
    } catch (RuntimeException ex) {
      log.warn("notify_dedupe_check_failed receiverUserId={} type={}", receiverUserId, type, ex);
      return false;
    }
  }

  private String resolveNickname(String userId) {
    if (!StringUtils.hasText(userId)) {
      return null;
    }
    return authUserRepository
        .findById(userId.trim())
        .map(AuthUserJpaEntity::getUsername)
        .filter(StringUtils::hasText)
        .orElse(null);
  }

  private String resolveSpecimenTitle(String specimenId) {
    if (!StringUtils.hasText(specimenId)) {
      return DEFAULT_SPECIMEN_TITLE;
    }
    return specimenRepository
        .findById(specimenId.trim())
        .map(SpecimenJpaEntity::getRepoFullName)
        .filter(StringUtils::hasText)
        .orElse(DEFAULT_SPECIMEN_TITLE);
  }

  private String buildReplyBody(String actorNickname, String specimenTitle) {
    String actor = StringUtils.hasText(actorNickname) ? actorNickname.trim() : DEFAULT_ACTOR_NICKNAME;
    if (!StringUtils.hasText(specimenTitle)) {
      return actor + "回复了你的处方";
    }
    return actor + "回复了你在『" + specimenTitle.trim() + "』下的处方";
  }

  private String buildMentionBody(String actorNickname, String specimenTitle) {
    String actor = StringUtils.hasText(actorNickname) ? actorNickname.trim() : DEFAULT_ACTOR_NICKNAME;
    if (!StringUtils.hasText(specimenTitle)) {
      return actor + "在评论中 @提及了你";
    }
    return actor + "在『" + specimenTitle.trim() + "』的评论中 @提及了你";
  }

  private String buildResonanceBodySingle(String actorNickname) {
    String actor = StringUtils.hasText(actorNickname) ? actorNickname.trim() : DEFAULT_ACTOR_NICKNAME;
    return actor + "复议了你的处方";
  }

  private String buildResonanceBodyAggregate(int aggregateCount) {
    return "又有 " + aggregateCount + " 位病友复议了你的处方";
  }

  private String buildAchievementUnlockedBody(String displayName, Integer rewardBug) {
    String achievementName =
        StringUtils.hasText(displayName) ? displayName.trim() : DEFAULT_ACHIEVEMENT_NAME;
    int normalizedRewardBug = rewardBug == null ? 0 : rewardBug;
    if (normalizedRewardBug > 0) {
      return "已解锁『" + achievementName + "』，奖励 +" + normalizedRewardBug + " BUG";
    }
    return "已解锁『" + achievementName + "』";
  }

  private String buildTargetUrl(String specimenId, String commentId) {
    return SPECIMEN_PATH_PREFIX + specimenId.trim() + COMMENT_ANCHOR_PREFIX + commentId.trim();
  }

  private String buildFallbackUrl(String specimenId) {
    return SPECIMEN_PATH_PREFIX + specimenId.trim();
  }

  private String aggregationKey(String receiverUserId, String commentId) {
    return AGG_KEY_PREFIX + receiverUserId.trim() + ":" + commentId.trim() + AGG_KEY_SUFFIX;
  }

  private void cacheAggregationAnchor(String key, Long notificationId) {
    if (notificationId == null) {
      return;
    }
    Duration ttl = aggregationWindow();
    try {
      if (ttl.isZero() || ttl.isNegative()) {
        redisTemplate.opsForValue().set(key, String.valueOf(notificationId));
      } else {
        redisTemplate.opsForValue().set(key, String.valueOf(notificationId), ttl);
      }
    } catch (RuntimeException ex) {
      log.warn("notify_aggregation_cache_write_failed key={}", key, ex);
    }
  }

  private Duration aggregationWindow() {
    int minutes = Math.max(0, policyProperties.getAggregationWindowMinutes());
    return Duration.ofMinutes(minutes);
  }

  private void incrementUnreadCount(String userId) {
    try {
      redisTemplate.opsForValue().increment(unreadKey(userId));
    } catch (RuntimeException ex) {
      log.warn("notify_unread_cache_incr_failed userId={}", userId, ex);
    }
  }

  private String unreadKey(String userId) {
    return policyProperties.getUnreadKeyPrefix() + userId;
  }

  private String readCacheValue(String key) {
    try {
      return redisTemplate.opsForValue().get(key);
    } catch (RuntimeException ex) {
      log.warn("notify_cache_read_failed key={}", key, ex);
      return null;
    }
  }

  private void deleteCacheKey(String key) {
    try {
      redisTemplate.delete(key);
    } catch (RuntimeException ex) {
      log.warn("notify_cache_delete_failed key={}", key, ex);
    }
  }

  private Long parseLong(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
