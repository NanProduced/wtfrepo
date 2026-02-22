package com.wtfrepo.backend.economy.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduler wrapper for M03 betting settlement orchestrator. */
@Component
@ConditionalOnBean(BettingSettlementOrchestratorService.class)
@ConditionalOnProperty(prefix = "app.betting.settlement", name = "enabled", havingValue = "true")
public class BettingSettlementScheduler {

  private static final Logger log = LoggerFactory.getLogger(BettingSettlementScheduler.class);

  private final BettingSettlementOrchestratorService bettingSettlementOrchestratorService;

  public BettingSettlementScheduler(
      BettingSettlementOrchestratorService bettingSettlementOrchestratorService) {
    this.bettingSettlementOrchestratorService = bettingSettlementOrchestratorService;
  }

  @Scheduled(cron = "${app.betting.settlement.orchestrator-cron:30 0 0 * * *}", zone = "UTC")
  public void runSettlementOrchestrator() {
    BettingSettlementOrchestratorService.SettlementRunResult result =
        bettingSettlementOrchestratorService.runSettlementNow();
    log.info(
        "betting_settlement_scheduled tradingDay={} cutoffClosedPools={} closedPoolsAttempted={} initialSettledPools={} retryCandidatePools={} retryAttempts={} retryRecoveredPools={} fallbackSettledPools={} fallbackFailedPools={}",
        result.tradingDay(),
        result.cutoffClosedPools(),
        result.closedPoolsAttempted(),
        result.initialSettledPools(),
        result.retryCandidatePools(),
        result.retryAttempts(),
        result.retryRecoveredPools(),
        result.fallbackSettledPools(),
        result.fallbackFailedPools());
  }
}
