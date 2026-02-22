package com.wtfrepo.backend.economy.application;

import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Economy-side external outbox event consumer application service.
 *
 * <p>This service intentionally keeps cross-module consumption logic centralized so handler classes
 * remain thin and transport-focused.
 */
@Service
public class BettingOutboxEventConsumerService {

  private static final Logger log = LoggerFactory.getLogger(BettingOutboxEventConsumerService.class);

  private final BettingService bettingService;

  public BettingOutboxEventConsumerService(BettingService bettingService) {
    this.bettingService = bettingService;
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
}
