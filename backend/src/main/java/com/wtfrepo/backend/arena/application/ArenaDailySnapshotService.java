package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.support.ArenaContractProperties;
import com.wtfrepo.backend.arena.application.support.ArenaTradingDayResolver;
import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase A/B settlement service for daily Elo snapshots and global mean-reversion correction.
 *
 * <p>Execution window temporarily toggles {@code settlementInProgress} so vote API can fail fast
 * with contract-defined 503 error while settlement jobs are running.
 */
@Service
@ConditionalOnBean({
  SpecimenRatingJpaRepository.class,
  BattleVoteJpaRepository.class,
  EloDailySnapshotJpaRepository.class
})
public class ArenaDailySnapshotService {

  private static final Logger log = LoggerFactory.getLogger(ArenaDailySnapshotService.class);

  private final ArenaContractProperties arenaContractProperties;
  private final ArenaRuntimePolicyPort arenaRuntimePolicyPort;
  private final ArenaSpecimenMatchReadModel specimenMatchReadModel;
  private final ArenaSpecimenRatingStore arenaSpecimenRatingStore;
  private final SpecimenRatingJpaRepository specimenRatingJpaRepository;
  private final BattleVoteJpaRepository battleVoteJpaRepository;
  private final EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;
  private final ArenaRatingMetricsMaintenanceService arenaRatingMetricsMaintenanceService;
  private final OutboxEventStore outboxEventStore;

  public ArenaDailySnapshotService(
      ArenaContractProperties arenaContractProperties,
      ArenaRuntimePolicyPort arenaRuntimePolicyPort,
      ArenaSpecimenMatchReadModel specimenMatchReadModel,
      ArenaSpecimenRatingStore arenaSpecimenRatingStore,
      SpecimenRatingJpaRepository specimenRatingJpaRepository,
      BattleVoteJpaRepository battleVoteJpaRepository,
      EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository,
      ArenaRatingMetricsMaintenanceService arenaRatingMetricsMaintenanceService,
      OutboxEventStore outboxEventStore) {
    this.arenaContractProperties = arenaContractProperties;
    this.arenaRuntimePolicyPort = arenaRuntimePolicyPort;
    this.specimenMatchReadModel = specimenMatchReadModel;
    this.arenaSpecimenRatingStore = arenaSpecimenRatingStore;
    this.specimenRatingJpaRepository = specimenRatingJpaRepository;
    this.battleVoteJpaRepository = battleVoteJpaRepository;
    this.eloDailySnapshotJpaRepository = eloDailySnapshotJpaRepository;
    this.arenaRatingMetricsMaintenanceService = arenaRatingMetricsMaintenanceService;
    this.outboxEventStore = outboxEventStore;
  }

  @Transactional
  public SnapshotRunResult runSettlementNow() {
    return runSettlementForDate(ArenaTradingDayResolver.settlementTradingDay(Instant.now()));
  }

  @Transactional
  public SnapshotRunResult runSettlementForDate(LocalDate tradingDay) {
    boolean previousSettlementFlag = arenaContractProperties.isSettlementInProgress();
    arenaContractProperties.setSettlementInProgress(true);

    try {
      Set<String> activeSpecimenIds = resolveActiveSpecimenIds();
      if (activeSpecimenIds.isEmpty()) {
        log.info("arena_settlement_skip_no_active tradingDay={}", tradingDay);
        return new SnapshotRunResult(tradingDay, 0, 0, 0);
      }

      List<SpecimenRatingJpaEntity> lockedRatings = lockActiveRatings(activeSpecimenIds);
      if (lockedRatings.isEmpty()) {
        log.warn("arena_settlement_skip_no_ratings tradingDay={} activeCount={}", tradingDay, activeSpecimenIds.size());
        return new SnapshotRunResult(tradingDay, 0, 0, activeSpecimenIds.size());
      }

      Instant fromInclusive = tradingDay.atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant toExclusive = tradingDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

      List<EloDailySnapshotJpaEntity> snapshots = new ArrayList<>(lockedRatings.size());
      for (SpecimenRatingJpaEntity rating : lockedRatings) {
        snapshots.add(buildSnapshot(tradingDay, rating, fromInclusive, toExclusive));
      }

      int correction = computeGlobalCorrection(lockedRatings);
      if (correction != 0) {
        lockedRatings.forEach(rating -> rating.applyGlobalCorrection(correction));
      }

      // Next trading day opens from corrected close, so later deltaR remains pure close-open.
      lockedRatings.forEach(SpecimenRatingJpaEntity::rollOpenToCurrentElo);
      snapshots.forEach(snapshot -> snapshot.setGlobalCorrection(correction));

      eloDailySnapshotJpaRepository.saveAll(snapshots);
      eloDailySnapshotJpaRepository.flush();

      // Contract-aligned batch recompute (non-vote-path):
      // 1) recent_appearances over configurable sliding window (default 24h),
      // 2) delta_r_7d_stddev over latest 7 trading-day snapshots.
      arenaRatingMetricsMaintenanceService.refreshForLockedRatings(
          lockedRatings, tradingDay, toExclusive);

      specimenRatingJpaRepository.saveAll(lockedRatings);
      appendDailySnapshotOutboxEvents(tradingDay, snapshots);

      int skipped = activeSpecimenIds.size() - lockedRatings.size();
      log.info(
          "arena_settlement_success tradingDay={} snapshots={} correction={} skipped={}",
          tradingDay,
          lockedRatings.size(),
          correction,
          skipped);
      return new SnapshotRunResult(tradingDay, lockedRatings.size(), correction, skipped);
    } finally {
      arenaContractProperties.setSettlementInProgress(previousSettlementFlag);
    }
  }

  private Set<String> resolveActiveSpecimenIds() {
    Set<String> activeIds = new TreeSet<>();
    specimenMatchReadModel
        .listActiveCandidates()
        .forEach(candidate -> activeIds.add(candidate.specimenId()));
    return activeIds;
  }

  private List<SpecimenRatingJpaEntity> lockActiveRatings(Set<String> activeSpecimenIds) {
    List<SpecimenRatingJpaEntity> ratings =
        specimenRatingJpaRepository.findAllBySpecimenIdInForUpdate(activeSpecimenIds);

    Set<String> missingSpecimenIds = new HashSet<>(activeSpecimenIds);
    ratings.forEach(rating -> missingSpecimenIds.remove(rating.getSpecimenId()));
    for (String missingSpecimenId : missingSpecimenIds) {
      // Bootstrap migration path from legacy metrics if this is an old specimen row.
      arenaSpecimenRatingStore.findForUpdate(missingSpecimenId);
    }

    return specimenRatingJpaRepository.findAllBySpecimenIdInForUpdate(activeSpecimenIds);
  }

  private EloDailySnapshotJpaEntity buildSnapshot(
      LocalDate tradingDay,
      SpecimenRatingJpaEntity rating,
      Instant fromInclusive,
      Instant toExclusive) {
    String specimenId = rating.getSpecimenId();
    int eloOpen = rating.getEloOpenToday();
    int eloClose = rating.getEloScore();
    int deltaR = eloClose - eloOpen;
    int matchesCount =
        countToInt(
            battleVoteJpaRepository.countVotesForSpecimenBetween(
                specimenId, fromInclusive, toExclusive));
    int bothBadCount =
        countToInt(
            battleVoteJpaRepository.countVotesForSpecimenAndWinnerBetween(
                specimenId, ArenaVoteWinner.BOTH_BAD, fromInclusive, toExclusive));

    return eloDailySnapshotJpaRepository
        .findBySpecimenIdAndDate(specimenId, tradingDay)
        .map(
            existing -> {
              existing.refresh(eloOpen, eloClose, deltaR, matchesCount, bothBadCount);
              return existing;
            })
        .orElseGet(
            () ->
                EloDailySnapshotJpaEntity.create(
                    specimenId, tradingDay, eloOpen, eloClose, deltaR, matchesCount, bothBadCount));
  }

  private int computeGlobalCorrection(List<SpecimenRatingJpaEntity> ratings) {
    int targetElo = arenaRuntimePolicyPort.currentArenaRuntimePolicy().initialElo();
    double averageElo = ratings.stream().mapToInt(SpecimenRatingJpaEntity::getEloScore).average().orElse(targetElo);
    int correction = (int) Math.round(targetElo - averageElo);
    return Math.abs(correction) >= 2 ? correction : 0;
  }

  private int countToInt(long count) {
    if (count <= 0) {
      return 0;
    }
    return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
  }

  private void appendDailySnapshotOutboxEvents(
      LocalDate tradingDay, List<EloDailySnapshotJpaEntity> snapshots) {
    for (EloDailySnapshotJpaEntity snapshot : snapshots) {
      DailySnapshotCreatedEventPayload payload =
          new DailySnapshotCreatedEventPayload(
              snapshot.getSpecimenId(),
              tradingDay,
              snapshot.getEloOpen(),
              snapshot.getEloClose(),
              snapshot.getDeltaR());
      outboxEventStore.append(
          new OutboxEventCommand(
              "ARENA_SPECIMEN",
              snapshot.getSpecimenId(),
              "DailySnapshotCreatedEvent",
              "arena:daily-snapshot:"
                  + snapshot.getSpecimenId()
                  + ":"
                  + snapshot.getDate(),
              payload,
              Instant.now()));
    }
  }

  public record SnapshotRunResult(
      LocalDate tradingDay, int processedSpecimens, int globalCorrection, int skippedSpecimens) {}

  private record DailySnapshotCreatedEventPayload(
      String specimenId, LocalDate date, int eloOpen, int eloClose, int deltaR) {}
}
