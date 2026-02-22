package com.wtfrepo.backend.economy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.economy.application.policy.EconomyPolicyPort;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicySnapshot;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyDailyClaimJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyWalletJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyDailyClaimJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyLedgerJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyWalletJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EconomyWalletServiceTest {

  @Mock private EconomyWalletJpaRepository walletRepository;
  @Mock private EconomyDailyClaimJpaRepository dailyClaimRepository;
  @Mock private EconomyLedgerJpaRepository ledgerRepository;
  @Mock private EconomyWalletBootstrapPort walletBootstrapPort;
  @Mock private EconomyPolicyPort economyPolicyPort;
  @Mock private OutboxEventStore outboxEventStore;

  private EconomyWalletService walletService;

  @BeforeEach
  void setUp() {
    walletService =
        new EconomyWalletService(
            walletRepository,
            dailyClaimRepository,
            ledgerRepository,
            walletBootstrapPort,
            economyPolicyPort,
            outboxEventStore);
  }

  @Test
  void credit_shouldAppendBugBalanceChangedOutboxEvent() {
    EconomyWalletJpaEntity wallet = EconomyWalletJpaEntity.create("usr_1", 1000L);
    when(ledgerRepository.findByIdempotencyKey("idem-credit-1"))
        .thenReturn(Optional.empty(), Optional.empty());
    when(walletRepository.findByUserIdForUpdate("usr_1")).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(EconomyWalletJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(ledgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    long balanceAfter =
        walletService.credit(
            new EconomyWalletService.CreditCommand(
                "usr_1",
                200,
                "GAME",
                "FLAPPY_DUKE",
                "idem-credit-1",
                "policy-v1",
                "property"),
            EconomyLedgerType.GAME_REWARD);

    assertThat(balanceAfter).isEqualTo(1200L);
    verify(outboxEventStore)
        .append(
            argThat(
                (OutboxEventCommand command) -> {
                  java.util.Map<String, Object> payload = toPayloadMap(command.payload());
                  return command.eventType().equals("BugBalanceChangedEvent")
                      && command.aggregateId().equals("usr_1")
                      && payload.keySet()
                          .equals(
                              java.util.Set.of("userId", "delta", "balanceAfter", "reason", "refId"))
                      && ((Number) payload.get("delta")).longValue() == 200L
                      && ((Number) payload.get("balanceAfter")).longValue() == 1200L;
                }));
  }

  @Test
  void claimDaily_shouldAppendDailyClaimedAndBalanceChangedEvents() {
    String userId = "usr_daily_1";
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    EconomyWalletJpaEntity wallet = EconomyWalletJpaEntity.create(userId, 1000L);

    when(dailyClaimRepository.findByUserIdAndTradingDay(userId, today)).thenReturn(Optional.empty());
    when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
    when(economyPolicyPort.currentPolicySnapshot())
        .thenReturn(new EconomyPolicySnapshot(500, 100, 2000, "policy-v1", "property"));
    when(dailyClaimRepository.saveAndFlush(any(EconomyDailyClaimJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(walletRepository.save(any(EconomyWalletJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(ledgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(
            dailyClaimRepository.findTop30ByUserIdAndTradingDayLessThanEqualOrderByTradingDayDesc(
                eq(userId), eq(today)))
        .thenReturn(
            List.of(
                EconomyDailyClaimJpaEntity.create(
                    userId, today, 500, 1500L, "policy-v1", "property"),
                EconomyDailyClaimJpaEntity.create(
                    userId, today.minusDays(1), 500, 1000L, "policy-v1", "property"),
                EconomyDailyClaimJpaEntity.create(
                    userId, today.minusDays(2), 500, 500L, "policy-v1", "property")));

    EconomyWalletService.DailyClaimResult result = walletService.claimDaily(userId);

    assertThat(result.claimed()).isTrue();
    assertThat(result.amount()).isEqualTo(500);
    assertThat(result.balanceAfter()).isEqualTo(1500L);
    verify(outboxEventStore)
        .append(
            argThat(
                (OutboxEventCommand command) -> {
                  java.util.Map<String, Object> payload = toPayloadMap(command.payload());
                  return command.eventType().equals("DailyClaimedEvent")
                      && command.aggregateId().equals(userId)
                      && payload
                          .keySet()
                          .equals(java.util.Set.of("userId", "amount", "claimDate", "consecutiveDays"))
                      && ((Number) payload.get("consecutiveDays")).intValue() == 3;
                }));
    verify(outboxEventStore)
        .append(
            argThat(
                (OutboxEventCommand command) -> {
                  java.util.Map<String, Object> payload = toPayloadMap(command.payload());
                  return command.eventType().equals("BugBalanceChangedEvent")
                      && command.aggregateId().equals(userId)
                      && payload.keySet()
                          .equals(
                              java.util.Set.of("userId", "delta", "balanceAfter", "reason", "refId"))
                      && ((Number) payload.get("delta")).longValue() == 500L
                      && ((Number) payload.get("balanceAfter")).longValue() == 1500L;
                }));
  }

  @SuppressWarnings("unchecked")
  private java.util.Map<String, Object> toPayloadMap(Object payload) {
    if (!(payload instanceof Record recordPayload)) {
      throw new IllegalArgumentException("payload must be a record type");
    }

    java.util.Map<String, Object> values = new java.util.HashMap<>();
    for (java.lang.reflect.RecordComponent component :
        recordPayload.getClass().getRecordComponents()) {
      try {
        values.put(component.getName(), component.getAccessor().invoke(recordPayload));
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("Failed to inspect payload record", ex);
      }
    }
    return values;
  }
}

