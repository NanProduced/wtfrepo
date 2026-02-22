package com.wtfrepo.backend.arena.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduler wrapper for daily snapshot and correction settlement job. */
@Component
@ConditionalOnBean(ArenaDailySnapshotService.class)
@ConditionalOnProperty(prefix = "app.arena.settlement", name = "enabled", havingValue = "true")
public class ArenaDailySnapshotScheduler {

  private static final Logger log = LoggerFactory.getLogger(ArenaDailySnapshotScheduler.class);

  private final ArenaDailySnapshotService arenaDailySnapshotService;

  public ArenaDailySnapshotScheduler(ArenaDailySnapshotService arenaDailySnapshotService) {
    this.arenaDailySnapshotService = arenaDailySnapshotService;
  }

  @Scheduled(cron = "${app.arena.settlement.snapshot-cron:0 0 0 * * *}", zone = "UTC")
  public void runDailySettlement() {
    ArenaDailySnapshotService.SnapshotRunResult result = arenaDailySnapshotService.runSettlementNow();
    log.info(
        "arena_settlement_scheduled tradingDay={} processed={} correction={} skipped={}",
        result.tradingDay(),
        result.processedSpecimens(),
        result.globalCorrection(),
        result.skippedSpecimens());
  }
}
