package com.wtfrepo.backend.economy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicyPort;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicySnapshot;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.GameSessionJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.GameSessionJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.GameTypeConfigJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EconomyGameServiceTest {

  @Mock private GameTypeConfigJpaRepository gameTypeConfigJpaRepository;
  @Mock private GameSessionJpaRepository gameSessionJpaRepository;
  @Mock private EconomyWalletService economyWalletService;
  @Mock private EconomyPolicyPort economyPolicyPort;
  @Mock private OutboxEventStore outboxEventStore;

  private EconomyGameService economyGameService;

  @BeforeEach
  void setUp() {
    economyGameService =
        new EconomyGameService(
            gameTypeConfigJpaRepository,
            gameSessionJpaRepository,
            economyWalletService,
            economyPolicyPort,
            new ObjectMapper(),
            outboxEventStore);
  }

  @Test
  void submitScore_shouldCreditWalletWhenValidationPasses() {
    when(economyPolicyPort.currentPolicySnapshot())
        .thenReturn(new EconomyPolicySnapshot(500, 100, 2000, "policy-v1", "property"));
    when(gameTypeConfigJpaRepository.findAllByOrderBySortOrderAscGameTypeKeyAsc())
        .thenReturn(java.util.List.of());
    when(gameSessionJpaRepository.findByIdempotencyKey("idem-game-1"))
        .thenReturn(java.util.Optional.empty());
    when(gameSessionJpaRepository.countByUserIdAndGameTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            eq("usr_1"), eq("FLAPPY_DUKE"), any(), any()))
        .thenReturn(2L);
    when(gameSessionJpaRepository.sumDailyBugEarned(eq("usr_1"), any(), any(), any())).thenReturn(45L);
    when(economyWalletService.currentBalance("usr_1")).thenReturn(2300L);
    when(economyWalletService.credit(any(), eq(EconomyLedgerType.GAME_REWARD))).thenReturn(2312L);
    when(gameSessionJpaRepository.save(any(GameSessionJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EconomyGameService.SubmitScoreResult result =
        economyGameService.submitScore(
            "usr_1",
            "idem-game-1",
            new EconomyGameService.SubmitScoreCommand(
                "FLAPPY_DUKE", 12, 45000, "client-1", Map.of("obstacles_passed", 12)));

    assertThat(result.gameType()).isEqualTo("FLAPPY_DUKE");
    assertThat(result.validationStatus()).isEqualTo("VALID");
    assertThat(result.bugEarned()).isEqualTo(12);
    assertThat(result.balanceAfter()).isEqualTo(2312L);
    verify(economyWalletService).credit(any(), eq(EconomyLedgerType.GAME_REWARD));
    verify(outboxEventStore)
        .append(
            argThat(
                (OutboxEventCommand command) -> {
                  java.util.Map<String, Object> payload = toPayloadMap(command.payload());
                  return command.eventType().equals("GameSessionCompletedEvent")
                      && payload.keySet()
                          .equals(
                              java.util.Set.of(
                                  "userId", "gameType", "score", "bugEarned", "validationStatus"))
                      && "VALID".equals(payload.get("validationStatus"));
                }));
  }

  @Test
  void submitScore_shouldRejectWhenDailyBugCapReached() {
    when(economyPolicyPort.currentPolicySnapshot())
        .thenReturn(new EconomyPolicySnapshot(500, 100, 2000, "policy-v1", "property"));
    when(gameTypeConfigJpaRepository.findAllByOrderBySortOrderAscGameTypeKeyAsc()).thenReturn(java.util.List.of());
    when(gameSessionJpaRepository.findByIdempotencyKey("idem-game-2")).thenReturn(java.util.Optional.empty());
    when(gameSessionJpaRepository.countByUserIdAndGameTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            eq("usr_1"), eq("FLAPPY_DUKE"), any(), any()))
        .thenReturn(0L);
    when(gameSessionJpaRepository.sumDailyBugEarned(eq("usr_1"), any(), any(), any())).thenReturn(2000L);
    when(economyWalletService.currentBalance("usr_1")).thenReturn(2300L);

    assertThatThrownBy(
            () ->
                economyGameService.submitScore(
                    "usr_1",
                    "idem-game-2",
                    new EconomyGameService.SubmitScoreCommand(
                        "FLAPPY_DUKE", 20, 30000, "client-2", Map.of())))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.GAME_DAILY_BUG_CAP_REACHED);

    verify(economyWalletService, never()).credit(any(), any());
    verify(outboxEventStore, never()).append(any());
  }

  @Test
  void listGameTypes_shouldReturnFallbackConfigsWhenDbNotSeededYet() {
    when(economyPolicyPort.currentPolicySnapshot())
        .thenReturn(new EconomyPolicySnapshot(500, 100, 2000, "policy-v1", "property"));
    when(gameTypeConfigJpaRepository.findAllByOrderBySortOrderAscGameTypeKeyAsc()).thenReturn(java.util.List.of());

    EconomyGameService.GameTypesView view = economyGameService.listGameTypes(null);

    assertThat(view.games()).hasSize(2);
    assertThat(view.games().get(0).gameType()).isEqualTo("FLAPPY_DUKE");
    assertThat(view.dailyGameBugCap()).isEqualTo(2000);
    assertThat(view.dailyGameBugEarned()).isNull();
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
