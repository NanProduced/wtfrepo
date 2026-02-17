package com.wtfrepo.backend.arena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArenaRatingMetricsMaintenanceServiceTest {

  @Mock
  private BattleVoteJpaRepository battleVoteJpaRepository;

  @Mock
  private EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;

  private ArenaMatchProperties arenaMatchProperties;
  private ArenaRatingMetricsMaintenanceService service;

  @BeforeEach
  void setUp() {
    arenaMatchProperties = new ArenaMatchProperties();
    arenaMatchProperties.setRecentAppearanceWindow(Duration.ofHours(24));
    service =
        new ArenaRatingMetricsMaintenanceService(
            arenaMatchProperties, battleVoteJpaRepository, eloDailySnapshotJpaRepository);
  }

  @Test
  void shouldRefreshRecentAppearancesAndDeltaRStddevForLockedRatings() {
    LocalDate tradingDay = LocalDate.of(2026, 2, 16);
    Instant recentWindowEndExclusive =
        tradingDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    SpecimenRatingJpaEntity specimenA =
        SpecimenRatingJpaEntity.createFromLegacyMetrics("spm_a", 1520, 20);
    SpecimenRatingJpaEntity specimenB =
        SpecimenRatingJpaEntity.createFromLegacyMetrics("spm_b", 1475, 8);
    List<SpecimenRatingJpaEntity> lockedRatings = List.of(specimenA, specimenB);

    when(battleVoteJpaRepository.countLeftAppearancesForSpecimensBetween(any(), any(), any()))
        .thenReturn(List.of(new AppearanceCount("spm_a", 3), new AppearanceCount("spm_b", 1)));
    when(battleVoteJpaRepository.countRightAppearancesForSpecimensBetween(any(), any(), any()))
        .thenReturn(List.of(new AppearanceCount("spm_a", 2)));

    when(
            eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(
                any(), eq(tradingDay.minusDays(6)), eq(tradingDay)))
        .thenReturn(
            List.of(
                EloDailySnapshotJpaEntity.create(
                    "spm_a", tradingDay.minusDays(1), 1500, 1505, 5, 3, 0),
                EloDailySnapshotJpaEntity.create(
                    "spm_a", tradingDay, 1505, 1502, -3, 4, 0),
                EloDailySnapshotJpaEntity.create("spm_b", tradingDay, 1475, 1475, 0, 2, 0)));

    service.refreshForLockedRatings(lockedRatings, tradingDay, recentWindowEndExclusive);

    assertThat(specimenA.getRecentAppearances()).isEqualTo(5);
    assertThat(specimenB.getRecentAppearances()).isEqualTo(1);
    assertThat(specimenA.getDeltaR7dStddev()).isCloseTo(4.0D, within(0.0001D));
    assertThat(specimenB.getDeltaR7dStddev()).isEqualTo(0.0D);
  }

  @Test
  void shouldFallbackToDefaultWindowWhenConfiguredRecentWindowIsInvalid() {
    arenaMatchProperties.setRecentAppearanceWindow(Duration.ZERO);

    LocalDate tradingDay = LocalDate.of(2026, 2, 16);
    Instant recentWindowEndExclusive =
        tradingDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    SpecimenRatingJpaEntity specimenA =
        SpecimenRatingJpaEntity.createFromLegacyMetrics("spm_a", 1500, 12);

    when(battleVoteJpaRepository.countLeftAppearancesForSpecimensBetween(any(), any(), any()))
        .thenReturn(List.of());
    when(battleVoteJpaRepository.countRightAppearancesForSpecimensBetween(any(), any(), any()))
        .thenReturn(List.of());
    when(eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    service.refreshForLockedRatings(List.of(specimenA), tradingDay, recentWindowEndExclusive);

    ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(battleVoteJpaRepository)
        .countLeftAppearancesForSpecimensBetween(any(), fromCaptor.capture(), toCaptor.capture());

    assertThat(Duration.between(fromCaptor.getValue(), toCaptor.getValue())).isEqualTo(Duration.ofHours(24));
  }

  private record AppearanceCount(String specimenId, long appearanceCount)
      implements BattleVoteJpaRepository.SpecimenAppearanceCountView {

    @Override
    public String getSpecimenId() {
      return specimenId;
    }

    @Override
    public long getAppearanceCount() {
      return appearanceCount;
    }
  }
}
