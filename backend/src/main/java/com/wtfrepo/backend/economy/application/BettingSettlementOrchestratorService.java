package com.wtfrepo.backend.economy.application;

import com.wtfrepo.backend.arena.application.support.ArenaTradingDayResolver;
import com.wtfrepo.backend.economy.application.support.BettingSettlementProperties;
import com.wtfrepo.backend.economy.domain.BetPoolStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetPoolJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * M03 settlement orchestrator for betting pools.
 *
 * <p>Current version executes:
 *
 * <ul>
 *   <li>cutoff fallback close for expired OPEN pools;
 *   <li>per-pool settlement execution with lock/status preconditions;
 *   <li>retry with exponential backoff and fallback force-settle.
 * </ul>
 */
@Service
public class BettingSettlementOrchestratorService {

  private static final Logger log = LoggerFactory.getLogger(BettingSettlementOrchestratorService.class);

  private final BetPoolJpaRepository betPoolJpaRepository;
  private final BettingService bettingService;
  private final BettingSettlementProperties settlementProperties;

  public BettingSettlementOrchestratorService(
      BetPoolJpaRepository betPoolJpaRepository,
      BettingService bettingService,
      BettingSettlementProperties settlementProperties) {
    this.betPoolJpaRepository = betPoolJpaRepository;
    this.bettingService = bettingService;
    this.settlementProperties = settlementProperties;
  }

  public SettlementRunResult runSettlementNow() {
    return runSettlementNow(Instant.now());
  }

  SettlementRunResult runSettlementNow(Instant now) {
    Instant executionAt = now == null ? Instant.now() : now;
    LocalDate tradingDay = ArenaTradingDayResolver.settlementTradingDay(executionAt);
    return runSettlementForDate(tradingDay, executionAt);
  }

  public SettlementRunResult runSettlementForDate(LocalDate tradingDay, Instant now) {
    Assert.notNull(tradingDay, "tradingDay must not be null");
    Instant executionAt = now == null ? Instant.now() : now;

    int cutoffClosedPools =
        betPoolJpaRepository.closeExpiredOpenPoolsForTradingDay(
            tradingDay, BetPoolStatus.OPEN, BetPoolStatus.CLOSED, executionAt);

    List<BetPoolJpaEntity> closedPools =
        betPoolJpaRepository.findAllByIdDateAndStatusOrderByIdSpecimenIdAsc(
            tradingDay, BetPoolStatus.CLOSED);

    List<String> snapshotMissingPools = new ArrayList<>();
    int initialSettledPools = 0;
    int retryCandidatePools = 0;
    int retryAttempts = 0;
    int retryRecoveredPools = 0;
    int fallbackSettledPools = 0;
    int fallbackFailedPools = 0;

    Instant retryDeadlineAt =
        executionAt.plusSeconds(Math.max(1L, settlementProperties.getRetryDeadlineSeconds()));
    for (BetPoolJpaEntity closedPool : closedPools) {
      BettingService.ClosedPoolSettlementResult settleResult =
          bettingService.settleClosedPool(closedPool.getSpecimenId(), tradingDay, executionAt);
      if (settleResult.settled()) {
        initialSettledPools++;
        continue;
      }
      if (isSnapshotMissingOutcome(settleResult.outcome())) {
        snapshotMissingPools.add(closedPool.getSpecimenId());
        continue;
      }
      retryCandidatePools++;

      RetryFallbackResult retryFallbackResult =
          runRetryAndFallback(
              closedPool.getSpecimenId(), tradingDay, retryDeadlineAt, settleResult.outcome());
      retryAttempts += retryFallbackResult.retryAttempts();
      if (retryFallbackResult.settledByRetry()) {
        retryRecoveredPools++;
      }
      if (retryFallbackResult.forceSettled()) {
        fallbackSettledPools++;
      }
      if (!retryFallbackResult.success()) {
        fallbackFailedPools++;
      }
    }

    SnapshotWaitResult snapshotWaitResult = SnapshotWaitResult.empty();
    if (!snapshotMissingPools.isEmpty()) {
      snapshotWaitResult =
          waitForSnapshotsAndSettle(snapshotMissingPools, tradingDay, retryDeadlineAt);
      initialSettledPools += snapshotWaitResult.settledAfterWait();
      retryCandidatePools += snapshotWaitResult.retryCandidatePools();
      retryAttempts += snapshotWaitResult.retryAttempts();
      retryRecoveredPools += snapshotWaitResult.retryRecoveredPools();
      fallbackSettledPools += snapshotWaitResult.fallbackSettledPools();
      fallbackFailedPools += snapshotWaitResult.fallbackFailedPools();
      log.info(
          "betting_settlement_snapshot_wait tradingDay={} waitingPools={} settledAfterWait={} stillMissing={} retryCandidatePools={} retryAttempts={} fallbackSettledPools={} fallbackFailedPools={}",
          tradingDay,
          snapshotMissingPools.size(),
          snapshotWaitResult.settledAfterWait(),
          snapshotWaitResult.stillMissingPools(),
          snapshotWaitResult.retryCandidatePools(),
          snapshotWaitResult.retryAttempts(),
          snapshotWaitResult.fallbackSettledPools(),
          snapshotWaitResult.fallbackFailedPools());
    }

    log.info(
        "betting_settlement_orchestrator_run tradingDay={} cutoffClosedPools={} closedPoolsAttempted={} initialSettledPools={} retryCandidatePools={} retryAttempts={} retryRecoveredPools={} fallbackSettledPools={} fallbackFailedPools={}",
        tradingDay,
        cutoffClosedPools,
        closedPools.size(),
        initialSettledPools,
        retryCandidatePools,
        retryAttempts,
        retryRecoveredPools,
        fallbackSettledPools,
        fallbackFailedPools);

    return new SettlementRunResult(
        tradingDay,
        cutoffClosedPools,
        closedPools.size(),
        initialSettledPools,
        retryCandidatePools,
        retryAttempts,
        retryRecoveredPools,
        fallbackSettledPools,
        fallbackFailedPools);
  }

  private RetryFallbackResult runRetryAndFallback(
      String specimenId,
      LocalDate tradingDay,
      Instant retryDeadlineAt,
      String initialSettlementOutcome) {
    int maxRetryAttempts = Math.max(0, settlementProperties.getRetryMaxAttempts());

    int retryAttempts = 0;
    String latestOutcome = initialSettlementOutcome;

    for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
      if (Instant.now().isAfter(retryDeadlineAt)) {
        return forceSettleAfterRetryFailure(
            specimenId,
            tradingDay,
            retryAttempts,
            "SETTLEMENT_RETRY_DEADLINE_REACHED_" + normalizeOutcomeToken(latestOutcome));
      }

      retryAttempts = attempt;
      BettingService.ClosedPoolSettlementResult retryResult =
          bettingService.settleClosedPool(specimenId, tradingDay, Instant.now());
      latestOutcome = retryResult.outcome();
      if (retryResult.settled()) {
        log.info(
            "betting_settlement_retry_settled specimenId={} tradingDay={} retryAttempts={} outcome={}",
            specimenId,
            tradingDay,
            retryAttempts,
            latestOutcome);
        return RetryFallbackResult.retrySettled(retryAttempts, latestOutcome);
      }

      sleepBeforeNextRetryIfNeeded(specimenId, tradingDay, attempt, maxRetryAttempts);
    }

    return forceSettleAfterRetryFailure(
        specimenId,
        tradingDay,
        retryAttempts,
        "SETTLEMENT_RETRY_EXHAUSTED_" + normalizeOutcomeToken(latestOutcome));
  }

  private RetryFallbackResult forceSettleAfterRetryFailure(
      String specimenId, LocalDate tradingDay, int retryAttempts, String triggerReason) {
    BettingService.ForceSettleResult forceSettleResult =
        bettingService.forceSettleAfterRetryExhausted(
            specimenId, tradingDay, Instant.now(), triggerReason);
    if (forceSettleResult.settled()) {
      log.warn(
          "betting_settlement_fallback_force_settle_applied specimenId={} tradingDay={} retryAttempts={} triggerReason={} outcome={}",
          specimenId,
          tradingDay,
          retryAttempts,
          triggerReason,
          forceSettleResult.outcome());
      return RetryFallbackResult.forceSettled(retryAttempts, forceSettleResult.outcome());
    }

    log.error(
        "betting_settlement_fallback_force_settle_failed specimenId={} tradingDay={} retryAttempts={} triggerReason={} outcome={}",
        specimenId,
        tradingDay,
        retryAttempts,
        triggerReason,
        forceSettleResult.outcome());
    return RetryFallbackResult.failed(retryAttempts, forceSettleResult.outcome());
  }

  private SnapshotWaitResult waitForSnapshotsAndSettle(
      List<String> snapshotMissingPools, LocalDate tradingDay, Instant retryDeadlineAt) {
    if (snapshotMissingPools.isEmpty()) {
      return SnapshotWaitResult.empty();
    }

    if (retryDeadlineAt == null || Instant.now().isAfter(retryDeadlineAt)) {
      return SnapshotWaitResult.pendingOnly(snapshotMissingPools.size());
    }

    List<String> pendingPools = new ArrayList<>(snapshotMissingPools);
    int settledAfterWait = 0;
    int retryCandidatePools = 0;
    int retryAttempts = 0;
    int retryRecoveredPools = 0;
    int fallbackSettledPools = 0;
    int fallbackFailedPools = 0;

    int attempt = 0;
    while (!pendingPools.isEmpty() && Instant.now().isBefore(retryDeadlineAt)) {
      List<String> stillMissing = new ArrayList<>();
      for (String specimenId : pendingPools) {
        BettingService.ClosedPoolSettlementResult retryResult =
            bettingService.settleClosedPool(specimenId, tradingDay, Instant.now());
        if (retryResult.settled()) {
          settledAfterWait++;
          continue;
        }
        if (isSnapshotMissingOutcome(retryResult.outcome())) {
          stillMissing.add(specimenId);
          continue;
        }

        retryCandidatePools++;
        RetryFallbackResult retryFallbackResult =
            runRetryAndFallback(specimenId, tradingDay, retryDeadlineAt, retryResult.outcome());
        retryAttempts += retryFallbackResult.retryAttempts();
        if (retryFallbackResult.settledByRetry()) {
          retryRecoveredPools++;
        }
        if (retryFallbackResult.forceSettled()) {
          fallbackSettledPools++;
        }
        if (!retryFallbackResult.success()) {
          fallbackFailedPools++;
        }
      }

      pendingPools = stillMissing;
      if (pendingPools.isEmpty() || Instant.now().isAfter(retryDeadlineAt)) {
        break;
      }
      sleepSnapshotWaitIfNeeded(++attempt);
    }

    return new SnapshotWaitResult(
        settledAfterWait,
        pendingPools.size(),
        retryCandidatePools,
        retryAttempts,
        retryRecoveredPools,
        fallbackSettledPools,
        fallbackFailedPools);
  }

  private void sleepSnapshotWaitIfNeeded(int attempt) {
    long backoffMs = resolveRetryBackoffMs(attempt);
    if (backoffMs <= 0L) {
      backoffMs = 1000L;
    }
    try {
      Thread.sleep(backoffMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("betting_settlement_snapshot_wait_interrupted attempt={} backoffMs={}", attempt, backoffMs);
    }
  }

  private boolean isSnapshotMissingOutcome(String outcome) {
    return "SNAPSHOT_MISSING".equals(outcome);
  }

  private void sleepBeforeNextRetryIfNeeded(
      String specimenId, LocalDate tradingDay, int attempt, int maxRetryAttempts) {
    if (attempt >= maxRetryAttempts) {
      return;
    }

    long backoffMs = resolveRetryBackoffMs(attempt);
    if (backoffMs <= 0L) {
      return;
    }

    try {
      Thread.sleep(backoffMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn(
          "betting_settlement_retry_sleep_interrupted specimenId={} tradingDay={} attempt={} backoffMs={}",
          specimenId,
          tradingDay,
          attempt,
          backoffMs);
    }
  }

  private long resolveRetryBackoffMs(int attempt) {
    long initialBackoffMs = Math.max(0L, settlementProperties.getRetryInitialBackoffMs());
    if (initialBackoffMs <= 0L) {
      return 0L;
    }

    int effectiveExponent = Math.max(0, attempt - 1);
    long multiplier = 1L << Math.min(30, effectiveExponent);
    long computedBackoff;
    try {
      computedBackoff = Math.multiplyExact(initialBackoffMs, multiplier);
    } catch (ArithmeticException ex) {
      computedBackoff = Long.MAX_VALUE;
    }

    long maxBackoffMs = Math.max(initialBackoffMs, settlementProperties.getRetryMaxBackoffMs());
    return Math.min(computedBackoff, maxBackoffMs);
  }

  private String normalizeOutcomeToken(String rawOutcome) {
    if (rawOutcome == null || rawOutcome.isBlank()) {
      return "UNKNOWN";
    }
    return rawOutcome.trim().replace(' ', '_');
  }

  /** Summary of one orchestrator run for observability and tests. */
  public record SettlementRunResult(
      LocalDate tradingDay,
      int cutoffClosedPools,
      int closedPoolsAttempted,
      int initialSettledPools,
      int retryCandidatePools,
      int retryAttempts,
      int retryRecoveredPools,
      int fallbackSettledPools,
      int fallbackFailedPools) {}

  private record RetryFallbackResult(
      int retryAttempts,
      boolean settledByRetry,
      boolean forceSettled,
      boolean success,
      String outcome) {

    private static RetryFallbackResult retrySettled(int retryAttempts, String outcome) {
      return new RetryFallbackResult(retryAttempts, true, false, true, outcome);
    }

    private static RetryFallbackResult forceSettled(int retryAttempts, String outcome) {
      return new RetryFallbackResult(retryAttempts, false, true, true, outcome);
    }

    private static RetryFallbackResult failed(int retryAttempts, String outcome) {
      return new RetryFallbackResult(retryAttempts, false, false, false, outcome);
    }
  }

  private record SnapshotWaitResult(
      int settledAfterWait,
      int stillMissingPools,
      int retryCandidatePools,
      int retryAttempts,
      int retryRecoveredPools,
      int fallbackSettledPools,
      int fallbackFailedPools) {

    private static SnapshotWaitResult empty() {
      return new SnapshotWaitResult(0, 0, 0, 0, 0, 0, 0);
    }

    private static SnapshotWaitResult pendingOnly(int pendingPools) {
      return new SnapshotWaitResult(0, pendingPools, 0, 0, 0, 0, 0);
    }
  }
}
