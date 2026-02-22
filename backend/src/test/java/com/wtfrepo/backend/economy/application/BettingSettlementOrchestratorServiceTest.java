package com.wtfrepo.backend.economy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.economy.application.support.BettingSettlementProperties;
import com.wtfrepo.backend.economy.domain.BetPoolStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetPoolJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BettingSettlementOrchestratorServiceTest {

  @Mock private BetPoolJpaRepository betPoolJpaRepository;
  @Mock private BettingService bettingService;

  private BettingSettlementOrchestratorService orchestratorService;
  private BettingSettlementProperties settlementProperties;

  @BeforeEach
  void setUp() {
    settlementProperties = new BettingSettlementProperties();
    settlementProperties.setRetryMaxAttempts(2);
    settlementProperties.setRetryInitialBackoffMs(0L);
    settlementProperties.setRetryMaxBackoffMs(0L);
    settlementProperties.setRetryDeadlineSeconds(300L);
    orchestratorService =
        new BettingSettlementOrchestratorService(
            betPoolJpaRepository, bettingService, settlementProperties);
  }

  @Test
  void runSettlementForDate_shouldApplyForceSettleAfterRetryExhausted() {
    LocalDate tradingDay = LocalDate.parse("2026-02-18");
    Instant now = Instant.parse("2026-02-19T00:06:00Z");
    BetPoolJpaEntity firstClosedPool =
        BetPoolJpaEntity.createOpen(
            "sp_settlement_1",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            now.minusSeconds(300));
    firstClosedPool.markClosedIfOpen();
    BetPoolJpaEntity secondClosedPool =
        BetPoolJpaEntity.createOpen(
            "sp_settlement_2",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            now.minusSeconds(300));
    secondClosedPool.markClosedIfOpen();

    when(
            betPoolJpaRepository.closeExpiredOpenPoolsForTradingDay(
                tradingDay, BetPoolStatus.OPEN, BetPoolStatus.CLOSED, now))
        .thenReturn(1);
    when(betPoolJpaRepository.findAllByIdDateAndStatusOrderByIdSpecimenIdAsc(
            tradingDay, BetPoolStatus.CLOSED))
        .thenReturn(List.of(firstClosedPool, secondClosedPool));
    when(bettingService.settleClosedPool("sp_settlement_1", tradingDay, now))
        .thenReturn(
            new BettingService.ClosedPoolSettlementResult(
                tradingDay, "sp_settlement_1", "SETTLED_UP", true, true));
    when(bettingService.settleClosedPool(eq("sp_settlement_2"), eq(tradingDay), any(Instant.class)))
        .thenReturn(
            new BettingService.ClosedPoolSettlementResult(
                tradingDay, "sp_settlement_2", "SNAPSHOT_MISSING", true, false),
            new BettingService.ClosedPoolSettlementResult(
                tradingDay, "sp_settlement_2", "SNAPSHOT_MISSING", true, false),
            new BettingService.ClosedPoolSettlementResult(
                tradingDay, "sp_settlement_2", "SNAPSHOT_MISSING", true, false));
    when(
            bettingService.forceSettleAfterRetryExhausted(
                eq("sp_settlement_2"),
                eq(tradingDay),
                any(Instant.class),
                eq("SETTLEMENT_RETRY_EXHAUSTED_SNAPSHOT_MISSING")))
        .thenReturn(
            new BettingService.ForceSettleResult(
                tradingDay,
                "FORCE_SETTLED",
                BetPoolStatus.CLOSED.name(),
                BetPoolStatus.SETTLED.name(),
                2,
                1,
                300L,
                true));

    BettingSettlementOrchestratorService.SettlementRunResult result =
        orchestratorService.runSettlementForDate(tradingDay, now);

    assertThat(result.tradingDay()).isEqualTo(tradingDay);
    assertThat(result.cutoffClosedPools()).isEqualTo(1);
    assertThat(result.closedPoolsAttempted()).isEqualTo(2);
    assertThat(result.initialSettledPools()).isEqualTo(1);
    assertThat(result.retryCandidatePools()).isEqualTo(1);
    assertThat(result.retryAttempts()).isEqualTo(2);
    assertThat(result.retryRecoveredPools()).isZero();
    assertThat(result.fallbackSettledPools()).isEqualTo(1);
    assertThat(result.fallbackFailedPools()).isZero();
    verify(bettingService).settleClosedPool("sp_settlement_1", tradingDay, now);
    verify(bettingService).settleClosedPool("sp_settlement_2", tradingDay, now);
    verify(bettingService, times(3))
        .settleClosedPool(eq("sp_settlement_2"), eq(tradingDay), any(Instant.class));
    verify(bettingService)
        .forceSettleAfterRetryExhausted(
            eq("sp_settlement_2"),
            eq(tradingDay),
            any(Instant.class),
            eq("SETTLEMENT_RETRY_EXHAUSTED_SNAPSHOT_MISSING"));
  }

  @Test
  void runSettlementNow_shouldResolveSettlementTradingDayFromUtcWindow() {
    Instant now = Instant.parse("2026-02-19T00:03:00Z");
    LocalDate expectedTradingDay = LocalDate.parse("2026-02-18");

    when(
            betPoolJpaRepository.closeExpiredOpenPoolsForTradingDay(
                expectedTradingDay, BetPoolStatus.OPEN, BetPoolStatus.CLOSED, now))
        .thenReturn(0);
    when(betPoolJpaRepository.findAllByIdDateAndStatusOrderByIdSpecimenIdAsc(
            expectedTradingDay, BetPoolStatus.CLOSED))
        .thenReturn(List.of());

    BettingSettlementOrchestratorService.SettlementRunResult result =
        orchestratorService.runSettlementNow(now);

    assertThat(result.tradingDay()).isEqualTo(expectedTradingDay);
    assertThat(result.closedPoolsAttempted()).isZero();
    assertThat(result.retryAttempts()).isZero();
    assertThat(result.retryRecoveredPools()).isZero();
    assertThat(result.fallbackSettledPools()).isZero();
    assertThat(result.fallbackFailedPools()).isZero();
    verifyNoInteractions(bettingService);
  }

  @Test
  void runSettlementForDate_shouldRecoverByRetryWithoutForceSettle() {
    settlementProperties.setRetryMaxAttempts(3);

    LocalDate tradingDay = LocalDate.parse("2026-02-18");
    Instant now = Instant.parse("2026-02-19T00:06:00Z");
    BetPoolJpaEntity closedPool =
        BetPoolJpaEntity.createOpen(
            "sp_retry_success",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            now.minusSeconds(300));
    closedPool.markClosedIfOpen();

    when(
            betPoolJpaRepository.closeExpiredOpenPoolsForTradingDay(
                tradingDay, BetPoolStatus.OPEN, BetPoolStatus.CLOSED, now))
        .thenReturn(0);
    when(betPoolJpaRepository.findAllByIdDateAndStatusOrderByIdSpecimenIdAsc(
            tradingDay, BetPoolStatus.CLOSED))
        .thenReturn(List.of(closedPool));
    when(
            bettingService.settleClosedPool(
                eq("sp_retry_success"), eq(tradingDay), any(Instant.class)))
        .thenReturn(
            new BettingService.ClosedPoolSettlementResult(
                tradingDay, "sp_retry_success", "SNAPSHOT_MISSING", false, false),
            new BettingService.ClosedPoolSettlementResult(
                tradingDay, "sp_retry_success", "SETTLED_UP", true, true));

    BettingSettlementOrchestratorService.SettlementRunResult result =
        orchestratorService.runSettlementForDate(tradingDay, now);

    assertThat(result.closedPoolsAttempted()).isEqualTo(1);
    assertThat(result.initialSettledPools()).isZero();
    assertThat(result.retryCandidatePools()).isEqualTo(1);
    assertThat(result.retryAttempts()).isEqualTo(1);
    assertThat(result.retryRecoveredPools()).isEqualTo(1);
    assertThat(result.fallbackSettledPools()).isZero();
    assertThat(result.fallbackFailedPools()).isZero();
    verify(bettingService, never())
        .forceSettleAfterRetryExhausted(any(), any(), any(), any());
  }
}
