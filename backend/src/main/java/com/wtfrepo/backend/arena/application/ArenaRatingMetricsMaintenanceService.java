package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Batch refreshes arena derived rating metrics defined by match profile contract.
 *
 * <p>This service intentionally avoids per-vote metric updates. Contract fields such as
 * {@code recent_appearances} and {@code delta_r_7d_stddev} are maintained by periodic full/batch
 * recomputation to keep vote transaction path predictable.
 */
@Service
@ConditionalOnBean({BattleVoteJpaRepository.class, EloDailySnapshotJpaRepository.class})
public class ArenaRatingMetricsMaintenanceService {

  private static final Logger log = LoggerFactory.getLogger(ArenaRatingMetricsMaintenanceService.class);
  private static final Duration DEFAULT_RECENT_APPEARANCE_WINDOW = Duration.ofHours(24);
  private static final int DELTA_R_STDDEV_WINDOW_DAYS = 7;

  private final ArenaMatchProperties arenaMatchProperties;
  private final BattleVoteJpaRepository battleVoteJpaRepository;
  private final EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;

  public ArenaRatingMetricsMaintenanceService(
      ArenaMatchProperties arenaMatchProperties,
      BattleVoteJpaRepository battleVoteJpaRepository,
      EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository) {
    this.arenaMatchProperties = arenaMatchProperties;
    this.battleVoteJpaRepository = battleVoteJpaRepository;
    this.eloDailySnapshotJpaRepository = eloDailySnapshotJpaRepository;
  }

  /**
   * Recomputes recent appearances and 7-day delta-R standard deviation for locked ratings.
   *
   * <p>Caller owns transaction and lock scope; this method only mutates in-memory entities.
   */
  public void refreshForLockedRatings(
      List<SpecimenRatingJpaEntity> lockedRatings,
      LocalDate tradingDay,
      Instant recentWindowEndExclusive) {
    if (lockedRatings == null || lockedRatings.isEmpty()) {
      return;
    }

    Set<String> specimenIds = new LinkedHashSet<>();
    lockedRatings.forEach(rating -> specimenIds.add(rating.getSpecimenId()));

    Instant recentWindowStartInclusive =
        recentWindowEndExclusive.minus(resolveRecentAppearanceWindow());
    Map<String, Integer> recentAppearancesBySpecimenId =
        countRecentAppearances(specimenIds, recentWindowStartInclusive, recentWindowEndExclusive);
    Map<String, Double> deltaRStddevBySpecimenId = computeDeltaR7dStddev(specimenIds, tradingDay);

    for (SpecimenRatingJpaEntity rating : lockedRatings) {
      int recentAppearances = recentAppearancesBySpecimenId.getOrDefault(rating.getSpecimenId(), 0);
      double deltaR7dStddev = deltaRStddevBySpecimenId.getOrDefault(rating.getSpecimenId(), 0.0D);
      rating.refreshMatchProfileMetrics(recentAppearances, deltaR7dStddev);
    }

    log.info(
        "arena_rating_metrics_refreshed tradingDay={} specimenCount={} recentWindowStart={} recentWindowEnd={}",
        tradingDay,
        lockedRatings.size(),
        recentWindowStartInclusive,
        recentWindowEndExclusive);
  }

  private Duration resolveRecentAppearanceWindow() {
    Duration configured = arenaMatchProperties.getRecentAppearanceWindow();
    if (configured == null || configured.isZero() || configured.isNegative()) {
      return DEFAULT_RECENT_APPEARANCE_WINDOW;
    }
    return configured;
  }

  private Map<String, Integer> countRecentAppearances(
      Set<String> specimenIds, Instant fromInclusive, Instant toExclusive) {
    Map<String, Long> counters = new LinkedHashMap<>();
    specimenIds.forEach(specimenId -> counters.put(specimenId, 0L));

    mergeAppearanceCounts(
        counters,
        battleVoteJpaRepository.countLeftAppearancesForSpecimensBetween(
            specimenIds, fromInclusive, toExclusive));
    mergeAppearanceCounts(
        counters,
        battleVoteJpaRepository.countRightAppearancesForSpecimensBetween(
            specimenIds, fromInclusive, toExclusive));

    Map<String, Integer> result = new LinkedHashMap<>();
    counters.forEach((specimenId, count) -> result.put(specimenId, safeToInt(count)));
    return result;
  }

  private void mergeAppearanceCounts(
      Map<String, Long> aggregate,
      List<BattleVoteJpaRepository.SpecimenAppearanceCountView> partialCounts) {
    if (partialCounts == null || partialCounts.isEmpty()) {
      return;
    }

    for (BattleVoteJpaRepository.SpecimenAppearanceCountView partial : partialCounts) {
      String specimenId = partial.getSpecimenId();
      if (!aggregate.containsKey(specimenId)) {
        continue;
      }
      aggregate.compute(
          specimenId,
          (ignored, previous) -> (previous == null ? 0L : previous) + Math.max(0L, partial.getAppearanceCount()));
    }
  }

  private Map<String, Double> computeDeltaR7dStddev(Set<String> specimenIds, LocalDate tradingDay) {
    LocalDate fromInclusive = tradingDay.minusDays(DELTA_R_STDDEV_WINDOW_DAYS - 1L);
    List<EloDailySnapshotJpaEntity> snapshots =
        eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(
            specimenIds, fromInclusive, tradingDay);

    Map<String, RunningStddev> statsBySpecimenId = new LinkedHashMap<>();
    for (String specimenId : specimenIds) {
      statsBySpecimenId.put(specimenId, new RunningStddev());
    }
    for (EloDailySnapshotJpaEntity snapshot : snapshots) {
      RunningStddev stats = statsBySpecimenId.get(snapshot.getSpecimenId());
      if (stats != null) {
        stats.add(snapshot.getDeltaR());
      }
    }

    Map<String, Double> result = new LinkedHashMap<>();
    statsBySpecimenId.forEach((specimenId, stats) -> result.put(specimenId, stats.populationStddev()));
    return result;
  }

  private int safeToInt(long value) {
    if (value <= 0L) {
      return 0;
    }
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  /** Stable one-pass variance accumulator used for daily batch recomputation. */
  private static final class RunningStddev {

    private long count;
    private double mean;
    private double m2;

    void add(int value) {
      count += 1L;
      double delta = value - mean;
      mean += delta / count;
      double delta2 = value - mean;
      m2 += delta * delta2;
    }

    double populationStddev() {
      if (count <= 0L) {
        return 0.0D;
      }
      double variance = m2 / count;
      return Math.sqrt(Math.max(0.0D, variance));
    }
  }
}
