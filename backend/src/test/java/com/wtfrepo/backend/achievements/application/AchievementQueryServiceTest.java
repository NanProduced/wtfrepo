package com.wtfrepo.backend.achievements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.achievements.infra.persistence.entity.UserAchievementJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.repository.UserAchievementJpaRepository;
import com.wtfrepo.backend.shared.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementQueryServiceTest {

  @Mock
  private UserAchievementJpaRepository userAchievementRepository;

  private AchievementQueryService service;

  @BeforeEach
  void setUp() {
    service = new AchievementQueryService(userAchievementRepository, new AchievementCatalog());
  }

  @Test
  void listAllShouldMergeUnlockedAndLockedItems() {
    UserAchievementJpaEntity unlocked =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER,
            "evt_1",
            "{}",
            50,
            Instant.parse("2026-02-28T10:00:00Z"));
    when(userAchievementRepository.findByUserId("u_1")).thenReturn(List.of(unlocked));

    AchievementQueryService.ListResult result =
        service.listMyAchievements("u_1", new AchievementQueryService.ListQuery("all", null, null));

    assertThat(result.items()).hasSize(3);
    assertThat(result.items().get(0).achievementCode())
        .isEqualTo(AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER);
    assertThat(result.items().get(0).isUnlocked()).isTrue();
    assertThat(result.items().get(1).isUnlocked()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.hasMore()).isFalse();
  }

  @Test
  void listShouldRejectInvalidStatus() {
    assertThatThrownBy(
            () ->
                service.listMyAchievements(
                    "u_1", new AchievementQueryService.ListQuery("bad_status", null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("invalid_status");
  }

  @Test
  void summaryShouldCalculateCountsAndTierBreakdown() {
    UserAchievementJpaEntity unlockedNarrator =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER,
            "evt_1",
            "{}",
            50,
            Instant.parse("2026-02-27T10:00:00Z"));
    UserAchievementJpaEntity unlockedTicker =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_TICKER_SCALPER,
            "evt_2",
            "{}",
            120,
            Instant.parse("2026-02-28T10:00:00Z"));
    when(userAchievementRepository.findByUserId("u_1"))
        .thenReturn(List.of(unlockedNarrator, unlockedTicker));

    AchievementQueryService.SummaryResult result = service.summary("u_1");

    assertThat(result.totalAchievements()).isEqualTo(3);
    assertThat(result.unlockedCount()).isEqualTo(2);
    assertThat(result.unlockedPercentage()).isEqualTo(67);
    assertThat(result.totalBugEarned()).isEqualTo(170);
    assertThat(result.latestUnlock()).isNotNull();
    assertThat(result.latestUnlock().achievementCode())
        .isEqualTo(AchievementCatalog.CODE_TICKER_SCALPER);
    assertThat(result.tierBreakdown().get("BRONZE").total()).isEqualTo(1);
    assertThat(result.tierBreakdown().get("BRONZE").unlocked()).isEqualTo(1);
    assertThat(result.tierBreakdown().get("SILVER").total()).isEqualTo(2);
    assertThat(result.tierBreakdown().get("SILVER").unlocked()).isEqualTo(1);
  }

  @Test
  void listUnlockedShouldUseRepositoryCursorQuery() {
    UserAchievementJpaEntity unlocked1 =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_TICKER_SCALPER,
            "evt_1",
            "{}",
            120,
            Instant.parse("2026-02-28T10:00:00Z"));
    UserAchievementJpaEntity unlocked2 =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_CHAOS_FULL_ON,
            "evt_2",
            "{}",
            120,
            Instant.parse("2026-02-27T10:00:00Z"));
    UserAchievementJpaEntity unlocked3 =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER,
            "evt_3",
            "{}",
            50,
            Instant.parse("2026-02-26T10:00:00Z"));

    when(
            userAchievementRepository.findPageAfterCursor(
                ArgumentMatchers.eq("u_1"),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any()))
        .thenReturn(List.of(unlocked1, unlocked2, unlocked3));

    AchievementQueryService.ListResult result =
        service.listMyAchievements(
            "u_1", new AchievementQueryService.ListQuery("unlocked", null, 2));

    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).achievementCode())
        .isEqualTo(AchievementCatalog.CODE_TICKER_SCALPER);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(unlocked2.getId());
  }

  @Test
  void listAllShouldRejectInvalidIndexCursor() {
    assertThatThrownBy(
            () ->
                service.listMyAchievements(
                    "u_1", new AchievementQueryService.ListQuery("all", "idx_bad", 20)))
        .isInstanceOf(ApiException.class)
        .hasMessage("invalid_cursor");
  }

  @Test
  void detailShouldReturnAchievementView() {
    UserAchievementJpaEntity unlocked =
        UserAchievementJpaEntity.create(
            "u_1",
            AchievementCatalog.CODE_TICKER_SCALPER,
            "evt_9",
            "{}",
            120,
            Instant.parse("2026-02-28T11:00:00Z"));
    when(userAchievementRepository.findByUserIdAndAchievementCode("u_1", AchievementCatalog.CODE_TICKER_SCALPER))
        .thenReturn(Optional.of(unlocked));
    when(userAchievementRepository.countByAchievementCode(AchievementCatalog.CODE_TICKER_SCALPER))
        .thenReturn(12L);
    when(userAchievementRepository.countDistinctUserId()).thenReturn(40L);

    AchievementQueryService.DetailResult result =
        service.detail("u_1", AchievementCatalog.CODE_TICKER_SCALPER);

    assertThat(result.achievementCode()).isEqualTo(AchievementCatalog.CODE_TICKER_SCALPER);
    assertThat(result.isUnlocked()).isTrue();
    assertThat(result.unlockedAt()).isEqualTo(Instant.parse("2026-02-28T11:00:00Z"));
    assertThat(result.totalUnlockedCount()).isEqualTo(12L);
    assertThat(result.totalUnlockedPercentage()).isEqualTo(30.0d);
    verify(userAchievementRepository).countDistinctUserId();
  }

  @Test
  void detailPercentageShouldBeZeroWhenUnlockedUserBaseIsEmpty() {
    when(userAchievementRepository.findByUserIdAndAchievementCode("u_1", AchievementCatalog.CODE_TICKER_SCALPER))
        .thenReturn(Optional.empty());
    when(userAchievementRepository.countByAchievementCode(AchievementCatalog.CODE_TICKER_SCALPER))
        .thenReturn(5L);
    when(userAchievementRepository.countDistinctUserId()).thenReturn(0L);

    AchievementQueryService.DetailResult result =
        service.detail("u_1", AchievementCatalog.CODE_TICKER_SCALPER);

    assertThat(result.totalUnlockedCount()).isEqualTo(5L);
    assertThat(result.totalUnlockedPercentage()).isEqualTo(0.0d);
  }

  @Test
  void detailShouldRejectUnknownAchievementCode() {
    assertThatThrownBy(() -> service.detail("u_1", "ACH_UNKNOWN"))
        .isInstanceOf(ApiException.class)
        .hasMessage("ach_not_found");
  }
}
