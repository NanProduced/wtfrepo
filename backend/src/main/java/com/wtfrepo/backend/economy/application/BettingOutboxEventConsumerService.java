package com.wtfrepo.backend.economy.application;

import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Economy-side external outbox event consumer application service.
 *
 * <p>This service intentionally keeps cross-module consumption logic centralized so handler classes
 * remain thin and transport-focused.
 */
@Service
public class BettingOutboxEventConsumerService {

  private static final Logger log = LoggerFactory.getLogger(BettingOutboxEventConsumerService.class);
  private static final String CREDIT_REASON_ACHIEVEMENT = "ACHIEVEMENT";
  private static final String ACHIEVEMENT_IDEMPOTENCY_PREFIX = "achievement_";

  private final BettingService bettingService;
  private final EconomyWalletService economyWalletService;

  public BettingOutboxEventConsumerService(
      BettingService bettingService, EconomyWalletService economyWalletService) {
    this.bettingService = bettingService;
    this.economyWalletService = economyWalletService;
  }

  /**
   * Handles Arena {@code IpoCompletedEvent} in M03 betting context.
   *
   * <p>Current implementation initializes bet-pool eagerly when eligible. Settlement-related write
   * path is
   * still driven by settlement orchestration workflow.
   */
  public void onIpoCompleted(
      String eventId, String specimenId, Integer calibratedScore, Instant occurredAt) {
    BettingService.IpoPoolInitResult result =
        bettingService.initializePoolFromIpoEvent(specimenId, calibratedScore, occurredAt);

    log.info(
        "betting_outbox_ipo_completed_consumed eventId={} specimenId={} calibratedScore={} tradingDay={} created={} outcome={}",
        eventId,
        specimenId,
        calibratedScore,
        result.tradingDay(),
        result.created(),
        result.outcome());
  }

  /**
   * Handles Specimen {@code SpecimenDeactivatedEvent} by executing force-settle write path.
   *
   * <p>This call is idempotent in combination with outbox consumer dedupe and wallet idempotency
   * keys on refund writes.
   */
  public void onSpecimenDeactivated(String eventId, String specimenId, Instant occurredAt) {
    BettingService.ForceSettleResult result =
        bettingService.forceSettleBySpecimenDeactivated(eventId, specimenId, occurredAt);

    log.info(
        "betting_outbox_specimen_deactivated_consumed eventId={} specimenId={} tradingDay={} outcome={} poolStatusBefore={} poolStatusAfter={} cancelledOrders={} refundedOrders={} refundedAmount={} settled={}",
        eventId,
        specimenId,
        result.tradingDay(),
        result.outcome(),
        result.poolStatusBefore(),
        result.poolStatusAfter(),
        result.cancelledOrders(),
        result.refundedOrders(),
        result.refundedAmount(),
        result.settled());
  }

  /**
   * Handles Arena {@code DailySnapshotCreatedEvent} as audit/cache-refresh signal only.
   *
   * <p>Per contract, this event does not trigger settlement directly.
   */
  public void onDailySnapshotCreated(
      String eventId,
      String specimenId,
      LocalDate date,
      Integer eloOpen,
      Integer eloClose,
      Integer deltaR,
      Instant occurredAt) {
    log.info(
        "betting_outbox_daily_snapshot_received eventId={} specimenId={} date={} eloOpen={} eloClose={} deltaR={} occurredAt={} note=audit_only_no_settlement_trigger",
        eventId,
        specimenId,
        date,
        eloOpen,
        eloClose,
        deltaR,
        occurredAt);
  }

  /**
   * Handles Achievements {@code AchievementUnlockedEvent} by issuing reward credits to wallet.
   *
   * <p>Credit write stays idempotent via deterministic key format.
   */
  public void onAchievementUnlocked(
      String eventId,
      String userId,
      String achievementCode,
      Integer rewardBug,
      Instant occurredAt) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(achievementCode)) {
      log.warn(
          "economy_outbox_achievement_unlocked_skip eventId={} reason=missing_required_fields",
          eventId);
      return;
    }

    int normalizedRewardBug = rewardBug == null ? 0 : rewardBug;
    if (normalizedRewardBug <= 0) {
      log.info(
          "economy_outbox_achievement_unlocked_skip eventId={} userId={} achievementCode={} reason=non_positive_reward rewardBug={}",
          eventId,
          userId,
          achievementCode,
          normalizedRewardBug);
      return;
    }

    EconomyWalletService.LedgerWriteResult ledgerWriteResult =
        economyWalletService.creditBug(
            userId.trim(),
            normalizedRewardBug,
            CREDIT_REASON_ACHIEVEMENT,
            achievementCode.trim(),
            EconomyConstants.RefType.ACHIEVEMENT,
            achievementRewardIdempotencyKey(userId, achievementCode));

    log.info(
        "economy_outbox_achievement_unlocked_consumed eventId={} userId={} achievementCode={} rewardBug={} ledgerId={} balanceAfter={}",
        eventId,
        userId,
        achievementCode,
        normalizedRewardBug,
        ledgerWriteResult.ledgerId(),
        ledgerWriteResult.balanceAfter());
  }

  private String achievementRewardIdempotencyKey(String userId, String achievementCode) {
    return ACHIEVEMENT_IDEMPOTENCY_PREFIX + achievementCode.trim() + "_" + userId.trim();
  }
}
