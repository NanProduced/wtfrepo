package com.wtfrepo.backend.economy.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BettingOutboxEventConsumerServiceTest {

  @Mock
  private BettingService bettingService;

  @Mock
  private EconomyWalletService economyWalletService;

  private BettingOutboxEventConsumerService eventConsumerService;

  @BeforeEach
  void setUp() {
    eventConsumerService =
        new BettingOutboxEventConsumerService(bettingService, economyWalletService);
  }

  @Test
  void onIpoCompleted_shouldDelegateToBettingService() {
    Instant occurredAt = Instant.parse("2026-02-20T00:00:00Z");
    when(bettingService.initializePoolFromIpoEvent("spm_ipo_1", 1530, occurredAt))
        .thenReturn(new BettingService.IpoPoolInitResult(LocalDate.parse("2026-02-20"), true, "POOL_CREATED"));

    eventConsumerService.onIpoCompleted("evt-ipo-1", "spm_ipo_1", 1530, occurredAt);

    verify(bettingService).initializePoolFromIpoEvent("spm_ipo_1", 1530, occurredAt);
  }

  @Test
  void onIpoCompleted_shouldPropagateExceptionForRetry() {
    Instant occurredAt = Instant.parse("2026-02-20T00:00:00Z");
    when(bettingService.initializePoolFromIpoEvent("spm_ipo_2", 1512, occurredAt))
        .thenThrow(new IllegalStateException("ipo-failed"));

    assertThatThrownBy(
            () -> eventConsumerService.onIpoCompleted("evt-ipo-2", "spm_ipo_2", 1512, occurredAt))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ipo-failed");
  }

  @Test
  void onSpecimenDeactivated_shouldDelegateToBettingService() {
    Instant occurredAt = Instant.parse("2026-02-20T00:10:00Z");
    when(bettingService.forceSettleBySpecimenDeactivated("evt-deact-1", "spm_deact_1", occurredAt))
        .thenReturn(
            new BettingService.ForceSettleResult(
                LocalDate.parse("2026-02-20"),
                "FORCE_SETTLED",
                "OPEN",
                "SETTLED",
                2,
                2,
                400L,
                true));

    eventConsumerService.onSpecimenDeactivated("evt-deact-1", "spm_deact_1", occurredAt);

    verify(bettingService).forceSettleBySpecimenDeactivated("evt-deact-1", "spm_deact_1", occurredAt);
  }

  @Test
  void onSpecimenDeactivated_shouldPropagateExceptionForRetry() {
    Instant occurredAt = Instant.parse("2026-02-20T00:10:00Z");
    when(bettingService.forceSettleBySpecimenDeactivated(eq("evt-deact-2"), eq("spm_deact_2"), eq(occurredAt)))
        .thenThrow(new IllegalStateException("force-settle-failed"));

    assertThatThrownBy(
            () -> eventConsumerService.onSpecimenDeactivated("evt-deact-2", "spm_deact_2", occurredAt))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("force-settle-failed");
  }

  @Test
  void onDailySnapshotCreated_shouldStayAuditOnlyAndNotCallBettingService() {
    eventConsumerService.onDailySnapshotCreated(
        "evt-snapshot-1",
        "spm_3",
        LocalDate.parse("2026-02-19"),
        1520,
        1536,
        16,
        Instant.parse("2026-02-20T00:20:00Z"));

    verifyNoInteractions(bettingService);
  }

  @Test
  void onAchievementUnlocked_shouldCreditWalletWhenRewardPositive() {
    when(
            economyWalletService.creditBug(
                "u_ach_1",
                120,
                "ACHIEVEMENT",
                "ACH_TICKER_SCALPER",
                "ACHIEVEMENT",
                "achievement_ACH_TICKER_SCALPER_u_ach_1"))
        .thenReturn(new EconomyWalletService.LedgerWriteResult("ledger_1", 1820L));

    eventConsumerService.onAchievementUnlocked(
        "evt-ach-1",
        "u_ach_1",
        "ACH_TICKER_SCALPER",
        120,
        Instant.parse("2026-02-28T16:00:00Z"));

    verify(economyWalletService)
        .creditBug(
            "u_ach_1",
            120,
            "ACHIEVEMENT",
            "ACH_TICKER_SCALPER",
            "ACHIEVEMENT",
            "achievement_ACH_TICKER_SCALPER_u_ach_1");
  }

  @Test
  void onAchievementUnlocked_shouldSkipWhenRewardNonPositive() {
    eventConsumerService.onAchievementUnlocked(
        "evt-ach-2", "u_ach_2", "ACH_CHAOS_FULL_ON", 0, Instant.parse("2026-02-28T16:01:00Z"));

    verifyNoInteractions(economyWalletService);
  }
}
