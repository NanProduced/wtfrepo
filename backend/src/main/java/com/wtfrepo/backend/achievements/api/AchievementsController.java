package com.wtfrepo.backend.achievements.api;

import com.wtfrepo.backend.achievements.application.AchievementQueryService;
import com.wtfrepo.backend.achievements.application.support.AchievementsConstants;
import com.wtfrepo.backend.achievements.application.support.AchievementsExceptions;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MVP read APIs for user achievements. */
@RestController
@Validated
@RequestMapping("/api/v1/me/achievements")
public class AchievementsController {

  private final AchievementQueryService achievementQueryService;

  public AchievementsController(AchievementQueryService achievementQueryService) {
    this.achievementQueryService = achievementQueryService;
  }

  @GetMapping
  public ResponseEntity<AchievementsListResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
    String userId = requireUserId(jwt);
    AchievementQueryService.ListResult result =
        achievementQueryService.listMyAchievements(
            userId, new AchievementQueryService.ListQuery(status, cursor, limit));
    return ResponseEntity.ok(AchievementsListResponse.from(result));
  }

  @GetMapping("/summary")
  public ResponseEntity<AchievementsSummaryResponse> summary(@AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    AchievementQueryService.SummaryResult result = achievementQueryService.summary(userId);
    return ResponseEntity.ok(AchievementsSummaryResponse.from(result));
  }

  @GetMapping("/{achievementCode}")
  public ResponseEntity<AchievementDetailResponse> detail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String achievementCode) {
    String userId = requireUserId(jwt);
    AchievementQueryService.DetailResult result = achievementQueryService.detail(userId, achievementCode);
    return ResponseEntity.ok(AchievementDetailResponse.from(result));
  }

  private String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw AchievementsExceptions.unauthorized(AchievementsConstants.Message.AUTH_REQUIRED);
    }
    return jwt.getSubject().trim();
  }

  public record AchievementsListResponse(List<AchievementItemResponse> items, String nextCursor, boolean hasMore) {
    static AchievementsListResponse from(AchievementQueryService.ListResult value) {
      return new AchievementsListResponse(
          value.items().stream().map(AchievementItemResponse::from).toList(),
          value.nextCursor(),
          value.hasMore());
    }
  }

  public record AchievementItemResponse(
      String achievementCode,
      String displayName,
      String description,
      String iconUrl,
      String tier,
      boolean isSecret,
      boolean isUnlocked,
      String unlockedAt,
      int rewardBug) {
    static AchievementItemResponse from(AchievementQueryService.ListItem value) {
      return new AchievementItemResponse(
          value.achievementCode(),
          value.displayName(),
          value.description(),
          value.iconUrl(),
          value.tier(),
          value.isSecret(),
          value.isUnlocked(),
          value.unlockedAt() == null ? null : value.unlockedAt().toString(),
          value.rewardBug());
    }
  }

  public record AchievementsSummaryResponse(
      int totalAchievements,
      int unlockedCount,
      int unlockedPercentage,
      long totalBugEarned,
      LatestUnlockResponse latestUnlock,
      Map<String, TierBreakdownResponse> tierBreakdown) {
    static AchievementsSummaryResponse from(AchievementQueryService.SummaryResult value) {
      Map<String, TierBreakdownResponse> tierMap =
          value.tierBreakdown().entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      Map.Entry::getKey,
                      entry -> TierBreakdownResponse.from(entry.getValue()),
                      (left, right) -> right,
                      java.util.LinkedHashMap::new));
      return new AchievementsSummaryResponse(
          value.totalAchievements(),
          value.unlockedCount(),
          value.unlockedPercentage(),
          value.totalBugEarned(),
          LatestUnlockResponse.from(value.latestUnlock()),
          tierMap);
    }
  }

  public record LatestUnlockResponse(String achievementCode, String displayName, String unlockedAt) {
    static LatestUnlockResponse from(AchievementQueryService.LatestUnlock value) {
      if (value == null) {
        return null;
      }
      return new LatestUnlockResponse(
          value.achievementCode(),
          value.displayName(),
          value.unlockedAt() == null ? null : value.unlockedAt().toString());
    }
  }

  public record TierBreakdownResponse(int total, int unlocked) {
    static TierBreakdownResponse from(AchievementQueryService.TierBreakdown value) {
      return new TierBreakdownResponse(value.total(), value.unlocked());
    }
  }

  public record AchievementDetailResponse(
      String achievementCode,
      String displayName,
      String description,
      String iconUrl,
      String tier,
      int rewardBug,
      long totalUnlockedCount,
      double totalUnlockedPercentage,
      boolean isUnlocked,
      String unlockedAt) {
    static AchievementDetailResponse from(AchievementQueryService.DetailResult value) {
      return new AchievementDetailResponse(
          value.achievementCode(),
          value.displayName(),
          value.description(),
          value.iconUrl(),
          value.tier(),
          value.rewardBug(),
          value.totalUnlockedCount(),
          value.totalUnlockedPercentage(),
          value.isUnlocked(),
          value.unlockedAt() == null ? null : value.unlockedAt().toString());
    }
  }
}
