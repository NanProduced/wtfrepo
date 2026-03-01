package com.wtfrepo.backend.achievements.application;

import com.wtfrepo.backend.achievements.application.support.AchievementsConstants;
import com.wtfrepo.backend.achievements.application.support.AchievementsExceptions;
import com.wtfrepo.backend.achievements.infra.persistence.entity.UserAchievementJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.repository.UserAchievementJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Read-side query service for MVP achievements list and summary APIs. */
@Service
public class AchievementQueryService {

  private static final String LIST_CURSOR_PREFIX = "idx_";
  private static final int DEFAULT_LIST_LIMIT = 20;
  private static final int MAX_LIST_LIMIT = 100;
  private static final String DEFAULT_UNKNOWN_ACHIEVEMENT_ICON = "/icons/ach/unknown.svg";
  private static final String SECRET_LOCKED_ICON = "/icons/ach/locked.svg";

  private final UserAchievementJpaRepository userAchievementRepository;
  private final AchievementCatalog achievementCatalog;

  public AchievementQueryService(
      UserAchievementJpaRepository userAchievementRepository, AchievementCatalog achievementCatalog) {
    this.userAchievementRepository = userAchievementRepository;
    this.achievementCatalog = achievementCatalog;
  }

  @Transactional(readOnly = true)
  public ListResult listMyAchievements(String userId, ListQuery query) {
    String normalizedUserId = requireUserId(userId);
    StatusScope statusScope = resolveStatusScope(query.status());
    int limit = normalizeLimit(query.limit());

    if (statusScope == StatusScope.UNLOCKED) {
      return listUnlocked(normalizedUserId, query.cursor(), limit);
    }

    List<UserAchievementJpaEntity> unlockedRecords = userAchievementRepository.findByUserId(normalizedUserId);
    Map<String, UserAchievementJpaEntity> unlockedByCode = new LinkedHashMap<>();
    for (UserAchievementJpaEntity unlockedRecord : unlockedRecords) {
      unlockedByCode.putIfAbsent(unlockedRecord.getAchievementCode(), unlockedRecord);
    }

    List<ListItem> fullItems = new ArrayList<>();
    for (AchievementCatalog.Definition definition : achievementCatalog.definitions()) {
      UserAchievementJpaEntity unlockedRecord = unlockedByCode.get(definition.achievementCode());
      boolean unlocked = unlockedRecord != null;
      if (statusScope == StatusScope.LOCKED && unlocked) {
        continue;
      }
      fullItems.add(toListItem(definition, unlockedRecord));
    }

    int startIndex = parseIndexCursor(query.cursor());
    if (startIndex > fullItems.size()) {
      throw AchievementsExceptions.invalidCursor(AchievementsConstants.Message.INVALID_CURSOR);
    }
    int toIndex = Math.min(startIndex + limit, fullItems.size());
    List<ListItem> pageItems = fullItems.subList(startIndex, toIndex);
    boolean hasMore = toIndex < fullItems.size();
    String nextCursor = hasMore ? LIST_CURSOR_PREFIX + toIndex : null;
    return new ListResult(pageItems, nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public SummaryResult summary(String userId) {
    String normalizedUserId = requireUserId(userId);
    List<UserAchievementJpaEntity> unlockedRecords = userAchievementRepository.findByUserId(normalizedUserId);
    Set<String> unlockedCodes =
        unlockedRecords.stream().map(UserAchievementJpaEntity::getAchievementCode).collect(java.util.stream.Collectors.toSet());

    int total = achievementCatalog.definitions().size();
    int unlockedCount = (int) achievementCatalog.definitions().stream().map(AchievementCatalog.Definition::achievementCode).filter(unlockedCodes::contains).count();
    int unlockedPercentage = total == 0 ? 0 : (int) Math.round((unlockedCount * 100.0d) / total);
    long totalBugEarned = unlockedRecords.stream().mapToLong(UserAchievementJpaEntity::getRewardBug).sum();

    UserAchievementJpaEntity latestUnlock =
        unlockedRecords.stream()
            .max(Comparator.comparing(UserAchievementJpaEntity::getUnlockedAt))
            .orElse(null);
    LatestUnlock latestUnlockView = toLatestUnlock(latestUnlock);

    Map<String, TierBreakdown> tierBreakdown = buildTierBreakdown(unlockedCodes);
    return new SummaryResult(
        total, unlockedCount, unlockedPercentage, totalBugEarned, latestUnlockView, tierBreakdown);
  }

  @Transactional(readOnly = true)
  public DetailResult detail(String userId, String achievementCode) {
    String normalizedUserId = requireUserId(userId);
    AchievementCatalog.Definition definition =
        achievementCatalog
            .findByCode(achievementCode)
            .orElseThrow(() -> AchievementsExceptions.notFound(AchievementsConstants.Message.ACH_NOT_FOUND));
    UserAchievementJpaEntity unlockedRecord =
        userAchievementRepository
            .findByUserIdAndAchievementCode(normalizedUserId, definition.achievementCode())
            .orElse(null);

    long totalUnlockedCount = userAchievementRepository.countByAchievementCode(definition.achievementCode());
    // MVP freeze: use unlocked-user base (distinct users with >=1 unlocked achievement) as denominator.
    long totalDistinctUsers = userAchievementRepository.countDistinctUserId();
    double totalUnlockedPercentage =
        totalDistinctUsers <= 0 ? 0D : (totalUnlockedCount * 100.0d) / totalDistinctUsers;

    ListItem presentation = toListItem(definition, unlockedRecord);
    return new DetailResult(
        presentation.achievementCode(),
        presentation.displayName(),
        presentation.description(),
        presentation.iconUrl(),
        presentation.tier(),
        presentation.rewardBug(),
        totalUnlockedCount,
        roundPercentage(totalUnlockedPercentage),
        presentation.isUnlocked(),
        presentation.unlockedAt());
  }

  private ListResult listUnlocked(String userId, String cursor, int limit) {
    Instant cursorUnlockedAt = null;
    String cursorId = null;
    if (StringUtils.hasText(cursor)) {
      String normalizedCursor = cursor.trim();
      if (normalizedCursor.startsWith(LIST_CURSOR_PREFIX)) {
        throw AchievementsExceptions.invalidCursor(AchievementsConstants.Message.INVALID_CURSOR);
      }
      UserAchievementJpaEntity anchor =
          userAchievementRepository
              .findById(normalizedCursor)
              .filter(item -> userId.equals(item.getUserId()))
              .orElseThrow(
                  () ->
                      AchievementsExceptions.invalidCursor(
                          AchievementsConstants.Message.INVALID_CURSOR));
      cursorUnlockedAt = anchor.getUnlockedAt();
      cursorId = anchor.getId();
    }

    List<UserAchievementJpaEntity> fetched =
        userAchievementRepository.findPageAfterCursor(
            userId, cursorUnlockedAt, cursorId, PageRequest.of(0, limit + 1));
    boolean hasMore = fetched.size() > limit;
    List<UserAchievementJpaEntity> pageWindow = hasMore ? fetched.subList(0, limit) : fetched;
    List<ListItem> items = pageWindow.stream().map(this::toListItem).toList();
    String nextCursor = hasMore ? pageWindow.get(pageWindow.size() - 1).getId() : null;
    return new ListResult(items, nextCursor, hasMore);
  }

  private ListItem toListItem(UserAchievementJpaEntity unlockedRecord) {
    AchievementCatalog.Definition definition =
        achievementCatalog.findByCode(unlockedRecord.getAchievementCode()).orElse(null);
    if (definition == null) {
      return new ListItem(
          unlockedRecord.getAchievementCode(),
          unlockedRecord.getAchievementCode(),
          "已解锁成就",
          DEFAULT_UNKNOWN_ACHIEVEMENT_ICON,
          "BRONZE",
          false,
          true,
          unlockedRecord.getUnlockedAt(),
          unlockedRecord.getRewardBug());
    }
    return toListItem(definition, unlockedRecord);
  }

  private ListItem toListItem(
      AchievementCatalog.Definition definition, UserAchievementJpaEntity unlockedRecord) {
    boolean unlocked = unlockedRecord != null;
    String displayName = definition.displayName();
    String description = definition.description();
    String iconUrl = definition.iconUrl();
    if (definition.secret() && !unlocked) {
      displayName = AchievementsConstants.Message.SECRET_HIDDEN_NAME;
      description = AchievementsConstants.Message.SECRET_HIDDEN_DESCRIPTION;
      iconUrl = SECRET_LOCKED_ICON;
    }
    return new ListItem(
        definition.achievementCode(),
        displayName,
        description,
        iconUrl,
        definition.tier(),
        definition.secret(),
        unlocked,
        unlocked ? unlockedRecord.getUnlockedAt() : null,
        definition.rewardBug());
  }

  private LatestUnlock toLatestUnlock(UserAchievementJpaEntity latestUnlock) {
    if (latestUnlock == null) {
      return null;
    }
    AchievementCatalog.Definition definition =
        achievementCatalog.findByCode(latestUnlock.getAchievementCode()).orElse(null);
    String displayName = definition == null ? latestUnlock.getAchievementCode() : definition.displayName();
    return new LatestUnlock(latestUnlock.getAchievementCode(), displayName, latestUnlock.getUnlockedAt());
  }

  private Map<String, TierBreakdown> buildTierBreakdown(Set<String> unlockedCodes) {
    Map<String, int[]> counters = new LinkedHashMap<>();
    for (AchievementCatalog.Definition definition : achievementCatalog.definitions()) {
      int[] bucket = counters.computeIfAbsent(definition.tier(), ignored -> new int[] {0, 0});
      bucket[0] = bucket[0] + 1;
      if (unlockedCodes.contains(definition.achievementCode())) {
        bucket[1] = bucket[1] + 1;
      }
    }

    Map<String, TierBreakdown> result = new LinkedHashMap<>();
    for (Map.Entry<String, int[]> entry : counters.entrySet()) {
      result.put(entry.getKey(), new TierBreakdown(entry.getValue()[0], entry.getValue()[1]));
    }
    return result;
  }

  private String requireUserId(String userId) {
    if (!StringUtils.hasText(userId)) {
      throw AchievementsExceptions.unauthorized(AchievementsConstants.Message.AUTH_REQUIRED);
    }
    return userId.trim();
  }

  private StatusScope resolveStatusScope(String status) {
    if (!StringUtils.hasText(status)) {
      return StatusScope.ALL;
    }
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "ALL" -> StatusScope.ALL;
      case "UNLOCKED" -> StatusScope.UNLOCKED;
      case "LOCKED" -> StatusScope.LOCKED;
      default -> throw AchievementsExceptions.validation(AchievementsConstants.Message.INVALID_STATUS);
    };
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIST_LIMIT;
    }
    return Math.min(limit, MAX_LIST_LIMIT);
  }

  private double roundPercentage(double value) {
    return Math.round(value * 10.0d) / 10.0d;
  }

  private int parseIndexCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return 0;
    }
    String normalized = cursor.trim();
    if (!normalized.startsWith(LIST_CURSOR_PREFIX)) {
      throw AchievementsExceptions.invalidCursor(AchievementsConstants.Message.INVALID_CURSOR);
    }
    String rawIndex = normalized.substring(LIST_CURSOR_PREFIX.length());
    try {
      int index = Integer.parseInt(rawIndex);
      if (index < 0) {
        throw AchievementsExceptions.invalidCursor(AchievementsConstants.Message.INVALID_CURSOR);
      }
      return index;
    } catch (NumberFormatException ex) {
      throw AchievementsExceptions.invalidCursor(AchievementsConstants.Message.INVALID_CURSOR);
    }
  }

  public record ListQuery(String status, String cursor, Integer limit) {}

  public record ListResult(List<ListItem> items, String nextCursor, boolean hasMore) {}

  public record ListItem(
      String achievementCode,
      String displayName,
      String description,
      String iconUrl,
      String tier,
      boolean isSecret,
      boolean isUnlocked,
      Instant unlockedAt,
      int rewardBug) {}

  public record SummaryResult(
      int totalAchievements,
      int unlockedCount,
      int unlockedPercentage,
      long totalBugEarned,
      LatestUnlock latestUnlock,
      Map<String, TierBreakdown> tierBreakdown) {}

  public record DetailResult(
      String achievementCode,
      String displayName,
      String description,
      String iconUrl,
      String tier,
      int rewardBug,
      long totalUnlockedCount,
      double totalUnlockedPercentage,
      boolean isUnlocked,
      Instant unlockedAt) {}

  public record LatestUnlock(String achievementCode, String displayName, Instant unlockedAt) {}

  public record TierBreakdown(int total, int unlocked) {}

  private enum StatusScope {
    ALL,
    UNLOCKED,
    LOCKED
  }
}
