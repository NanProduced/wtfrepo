package com.wtfrepo.backend.achievements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.wtfrepo.backend.achievements.infra.outbox.NarratorPreferenceChangedOutboxEventHandler;
import com.wtfrepo.backend.achievements.infra.outbox.TickerItemClickedOutboxEventHandler;
import com.wtfrepo.backend.achievements.infra.outbox.VoteCompletedOutboxEventHandler;
import com.wtfrepo.backend.achievements.infra.persistence.entity.AchievementInboxEventJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.entity.UserAchievementJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.repository.AchievementInboxEventJpaRepository;
import com.wtfrepo.backend.achievements.infra.persistence.repository.UserAchievementJpaRepository;
import com.wtfrepo.backend.economy.application.BettingOutboxEventConsumerService;
import com.wtfrepo.backend.economy.infra.outbox.AchievementUnlockedOutboxEventHandler;
import com.wtfrepo.backend.economy.infra.outbox.EconomyOutboxPayloadReader;
import com.wtfrepo.backend.notifications.application.NotificationOutboxEventConsumerService;
import com.wtfrepo.backend.notifications.infra.outbox.NotificationOutboxPayloadReader;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementCloseoutFlowTest {

  @Mock private UserAchievementJpaRepository userAchievementRepository;
  @Mock private AchievementInboxEventJpaRepository inboxEventRepository;
  @Mock private ArenaVoteLookupPort arenaVoteLookupPort;
  @Mock private OutboxEventStore outboxEventStore;

  @Mock private BettingOutboxEventConsumerService economyConsumerService;
  @Mock private NotificationOutboxEventConsumerService notificationConsumerService;

  private AchievementInboxService inboxService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().findAndAddModules().build();

    AchievementsPolicyProperties policy = new AchievementsPolicyProperties();
    policy.setTickerToVoteWindow(Duration.ofMinutes(5));
    policy.setTickerClickLookbackSize(50);
    policy.setNarratorHistoryLookbackSize(64);

    AchievementEvaluationService evaluationService =
        new AchievementEvaluationService(
            new AchievementCatalog(),
            userAchievementRepository,
            inboxEventRepository,
            arenaVoteLookupPort,
            policy,
            outboxEventStore,
            objectMapper);
    inboxService = new AchievementInboxService(inboxEventRepository, evaluationService);

    when(inboxEventRepository.save(any(AchievementInboxEventJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userAchievementRepository.save(any(UserAchievementJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void narratorPreferenceChainShouldProduceUnlockAndBeConsumableByEconomyAndNotification() throws Exception {
    String userId = "u_closeout_1";
    Instant occurredAt = Instant.parse("2026-03-01T10:00:00Z");

    when(userAchievementRepository.existsByUserIdAndAchievementCode(
            userId, AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER))
        .thenReturn(false);

    OutboxStreamMessage narratorPreferenceChanged =
        message(
            "evt_narrator_pref_1",
            "NARRATOR_PREFERENCE",
            userId,
            "NarratorPreferenceChangedEvent",
            "narrator:preference:changed",
            "{\"userId\":\"u_closeout_1\",\"oldMode\":\"FULL\",\"newMode\":\"LITE\",\"tonePreference\":\"SAFE\"}",
            occurredAt);

    NarratorPreferenceChangedOutboxEventHandler narratorHandler =
        new NarratorPreferenceChangedOutboxEventHandler(inboxService);
    narratorHandler.handle(narratorPreferenceChanged);

    ArgumentCaptor<OutboxEventCommand> unlockEventCaptor =
        ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(unlockEventCaptor.capture());

    OutboxEventCommand unlockCommand = unlockEventCaptor.getValue();
    assertThat(unlockCommand.eventType()).isEqualTo("AchievementUnlockedEvent");
    assertThat(unlockCommand.payload().toString()).contains("ACH_NARRATOR_MODE_SWITCHER");
    assertThat(unlockCommand.payload().toString()).contains("rewardBug=50");

    OutboxStreamMessage unlockMessage =
        message(
            "evt_ach_unlock_1",
            "USER_ACHIEVEMENT",
            userId,
            "AchievementUnlockedEvent",
            unlockCommand.eventKey(),
            objectMapper.writeValueAsString(unlockCommand.payload()),
            unlockCommand.occurredAt());

    AchievementUnlockedOutboxEventHandler economyHandler =
        new AchievementUnlockedOutboxEventHandler(
            new EconomyOutboxPayloadReader(objectMapper), economyConsumerService);
    com.wtfrepo.backend.notifications.infra.outbox.AchievementUnlockedOutboxEventHandler
        notificationHandler =
            new com.wtfrepo.backend.notifications.infra.outbox.AchievementUnlockedOutboxEventHandler(
                new NotificationOutboxPayloadReader(objectMapper), notificationConsumerService);

    economyHandler.handle(unlockMessage);
    notificationHandler.handle(unlockMessage);

    verify(economyConsumerService)
        .onAchievementUnlocked(
            eq("evt_ach_unlock_1"),
            eq(userId),
            eq(AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER),
            eq(50),
            eq(unlockCommand.occurredAt()));
    verify(notificationConsumerService)
        .onAchievementUnlocked(
            eq("evt_ach_unlock_1"),
            eq(userId),
            eq(AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER),
            eq("Narrator Mode Switcher"),
            eq(50),
            eq(unlockCommand.occurredAt()));
  }

  @Test
  void tickerToVoteChainShouldUnlockTickerScalperViaInboxHandlers() {
    String userId = "u_closeout_2";
    Instant clickTime = Instant.parse("2026-03-01T12:00:00Z");
    Instant voteTime = Instant.parse("2026-03-01T12:03:00Z");

    AchievementInboxEventJpaEntity persistedClickEvent =
        AchievementInboxEventJpaEntity.of(
            "evt_ticker_click_chain_1",
            "TickerItemClickedEvent",
            "ticker:click:chain:1",
            "NARRATOR_TICKER",
            userId,
            "{\"eventId\":\"evt_ticker_1\",\"eventType\":\"ticker.duel_settled\",\"actionUrl\":\"/arena?battleId=battle_chain_1\"}",
            clickTime);

    when(arenaVoteLookupPort.findVoterUserIdByOrderId("order_chain_1")).thenReturn(Optional.of(userId));
    when(userAchievementRepository.existsByUserIdAndAchievementCode(
            userId, AchievementCatalog.CODE_TICKER_SCALPER))
        .thenReturn(false);
    when(inboxEventRepository.findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
            eq("TickerItemClickedEvent"), eq(userId), any()))
        .thenReturn(List.of(persistedClickEvent));

    OutboxStreamMessage clickMessage =
        message(
            "evt_ticker_click_chain_1",
            "NARRATOR_TICKER",
            userId,
            "TickerItemClickedEvent",
            "ticker:item:clicked",
            "{\"eventId\":\"evt_ticker_1\",\"eventType\":\"ticker.duel_settled\",\"actionUrl\":\"/arena?battleId=battle_chain_1\"}",
            clickTime);
    OutboxStreamMessage voteMessage =
        message(
            "evt_vote_chain_1",
            "ARENA_BATTLE",
            "battle_chain_1",
            "VoteCompletedEvent",
            "arena:vote:completed",
            "{\"battleId\":\"battle_chain_1\",\"orderId\":\"order_chain_1\",\"winner\":\"LEFT\"}",
            voteTime);

    TickerItemClickedOutboxEventHandler clickHandler = new TickerItemClickedOutboxEventHandler(inboxService);
    VoteCompletedOutboxEventHandler voteHandler = new VoteCompletedOutboxEventHandler(inboxService);

    clickHandler.handle(clickMessage);
    voteHandler.handle(voteMessage);

    ArgumentCaptor<OutboxEventCommand> unlockEventCaptor =
        ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(unlockEventCaptor.capture());
    OutboxEventCommand unlockCommand = unlockEventCaptor.getValue();

    assertThat(unlockCommand.eventType()).isEqualTo("AchievementUnlockedEvent");
    assertThat(unlockCommand.payload().toString()).contains("ACH_TICKER_SCALPER");
    assertThat(unlockCommand.payload().toString()).contains("rewardBug=120");
    assertThat(unlockCommand.payload().toString()).contains(userId);
  }

  private OutboxStreamMessage message(
      String eventId,
      String aggregateType,
      String aggregateId,
      String eventType,
      String eventKey,
      String payload,
      Instant occurredAt) {
    return new OutboxStreamMessage(
        "177-0",
        "wtfrepo:outbox:events",
        eventId,
        aggregateType,
        aggregateId,
        eventType,
        eventKey,
        payload,
        occurredAt,
        occurredAt,
        occurredAt,
        Map.of("eventId", eventId, "eventType", eventType));
  }
}
