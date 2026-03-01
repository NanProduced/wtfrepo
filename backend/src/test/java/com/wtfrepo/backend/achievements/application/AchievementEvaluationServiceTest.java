package com.wtfrepo.backend.achievements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.achievements.infra.persistence.entity.AchievementInboxEventJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.entity.UserAchievementJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.repository.AchievementInboxEventJpaRepository;
import com.wtfrepo.backend.achievements.infra.persistence.repository.UserAchievementJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementEvaluationServiceTest {

  @Mock
  private UserAchievementJpaRepository userAchievementRepository;

  @Mock
  private AchievementInboxEventJpaRepository inboxEventRepository;

  @Mock
  private ArenaVoteLookupPort arenaVoteLookupPort;

  @Mock
  private OutboxEventStore outboxEventStore;

  private AchievementEvaluationService service;

  @BeforeEach
  void setUp() {
    AchievementsPolicyProperties policy = new AchievementsPolicyProperties();
    policy.setTickerToVoteWindow(Duration.ofMinutes(5));
    policy.setTickerClickLookbackSize(50);
    policy.setNarratorHistoryLookbackSize(64);
    service =
        new AchievementEvaluationService(
            new AchievementCatalog(),
            userAchievementRepository,
            inboxEventRepository,
            arenaVoteLookupPort,
            policy,
            outboxEventStore,
            new com.fasterxml.jackson.databind.ObjectMapper());
  }

  @Test
  void evaluateNarratorModeSwitchShouldUnlockModeSwitcher() {
    AchievementInboxEventJpaEntity event =
        AchievementInboxEventJpaEntity.of(
            "evt_1",
            "NarratorPreferenceChangedEvent",
            "event:key:1",
            "NARRATOR_PREFERENCE",
            "u_1",
            "{\"userId\":\"u_1\",\"oldMode\":\"FULL\",\"newMode\":\"LITE\",\"tonePreference\":\"SAFE\"}",
            Instant.parse("2026-02-28T10:00:00Z"));

    when(userAchievementRepository.existsByUserIdAndAchievementCode(
            "u_1", "ACH_NARRATOR_MODE_SWITCHER"))
        .thenReturn(false);
    when(userAchievementRepository.save(any(UserAchievementJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.evaluateInboundEvent(event);

    verify(userAchievementRepository).save(any(UserAchievementJpaEntity.class));
    ArgumentCaptor<OutboxEventCommand> outboxCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(outboxCaptor.capture());
    OutboxEventCommand command = outboxCaptor.getValue();
    assertThat(command.eventType()).isEqualTo("AchievementUnlockedEvent");
    assertThat(command.eventKey()).isEqualTo("achievements:unlocked:ACH_NARRATOR_MODE_SWITCHER:u_1");
    assertThat(command.payload().toString()).contains("ACH_NARRATOR_MODE_SWITCHER");
    assertThat(command.payload().toString()).contains("u_1");
    assertThat(command.payload().toString()).contains("rewardBug=50");
  }

  @Test
  void evaluateNarratorFullStreakShouldUnlockChaosFullOn() {
    AchievementInboxEventJpaEntity event =
        AchievementInboxEventJpaEntity.of(
            "evt_2",
            "NarratorPreferenceChangedEvent",
            "event:key:2",
            "NARRATOR_PREFERENCE",
            "u_2",
            "{\"userId\":\"u_2\",\"oldMode\":\"LITE\",\"newMode\":\"FULL\",\"tonePreference\":\"EDGY\"}",
            Instant.parse("2026-02-28T09:00:00Z"));

    AchievementInboxEventJpaEntity day2 =
        AchievementInboxEventJpaEntity.of(
            "evt_2_d2",
            "NarratorPreferenceChangedEvent",
            "event:key:2:d2",
            "NARRATOR_PREFERENCE",
            "u_2",
            "{\"userId\":\"u_2\",\"oldMode\":\"OFF\",\"newMode\":\"FULL\"}",
            Instant.parse("2026-02-27T09:00:00Z"));
    AchievementInboxEventJpaEntity day1 =
        AchievementInboxEventJpaEntity.of(
            "evt_2_d1",
            "NarratorPreferenceChangedEvent",
            "event:key:2:d1",
            "NARRATOR_PREFERENCE",
            "u_2",
            "{\"userId\":\"u_2\",\"oldMode\":\"OFF\",\"newMode\":\"FULL\"}",
            Instant.parse("2026-02-26T09:00:00Z"));

    when(userAchievementRepository.existsByUserIdAndAchievementCode(
            "u_2", "ACH_NARRATOR_MODE_SWITCHER"))
        .thenReturn(true);
    when(userAchievementRepository.existsByUserIdAndAchievementCode("u_2", "ACH_CHAOS_FULL_ON"))
        .thenReturn(false);
    when(inboxEventRepository.findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
            eq("NarratorPreferenceChangedEvent"), eq("u_2"), any()))
        .thenReturn(List.of(event, day2, day1));
    when(userAchievementRepository.save(any(UserAchievementJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.evaluateInboundEvent(event);

    ArgumentCaptor<OutboxEventCommand> outboxCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(outboxCaptor.capture());
    assertThat(outboxCaptor.getValue().payload().toString()).contains("ACH_CHAOS_FULL_ON");
    assertThat(outboxCaptor.getValue().payload().toString()).contains("u_2");
    assertThat(outboxCaptor.getValue().payload().toString()).contains("rewardBug=120");
  }

  @Test
  void evaluateVoteCompletedShouldUnlockTickerScalperWhenCorrelatedClickExists() {
    AchievementInboxEventJpaEntity voteEvent =
        AchievementInboxEventJpaEntity.of(
            "evt_vote_1",
            "VoteCompletedEvent",
            "event:key:vote:1",
            "ARENA_BATTLE",
            "battle_1",
            "{\"battleId\":\"battle_1\",\"orderId\":\"order_1\",\"winner\":\"LEFT\"}",
            Instant.parse("2026-02-28T12:00:00Z"));
    AchievementInboxEventJpaEntity clickEvent =
        AchievementInboxEventJpaEntity.of(
            "evt_click_1",
            "TickerItemClickedEvent",
            "event:key:click:1",
            "NARRATOR_TICKER",
            "u_5",
            "{\"eventId\":\"evt_ticker_1\",\"eventType\":\"ticker.duel_settled\",\"actionUrl\":\"/arena?battleId=battle_1\"}",
            Instant.parse("2026-02-28T11:58:00Z"));

    when(arenaVoteLookupPort.findVoterUserIdByOrderId("order_1")).thenReturn(Optional.of("u_5"));
    when(userAchievementRepository.existsByUserIdAndAchievementCode("u_5", "ACH_TICKER_SCALPER"))
        .thenReturn(false);
    when(inboxEventRepository.findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
            eq("TickerItemClickedEvent"), eq("u_5"), any()))
        .thenReturn(List.of(clickEvent));
    when(userAchievementRepository.save(any(UserAchievementJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.evaluateInboundEvent(voteEvent);

    verify(userAchievementRepository).save(any(UserAchievementJpaEntity.class));
    ArgumentCaptor<OutboxEventCommand> outboxCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(outboxCaptor.capture());
    assertThat(outboxCaptor.getValue().payload().toString()).contains("ACH_TICKER_SCALPER");
    assertThat(outboxCaptor.getValue().payload().toString()).contains("u_5");
    assertThat(outboxCaptor.getValue().payload().toString()).contains("rewardBug=120");
  }

  @Test
  void evaluateVoteCompletedShouldSkipWhenNoCorrelatedTickerClick() {
    AchievementInboxEventJpaEntity voteEvent =
        AchievementInboxEventJpaEntity.of(
            "evt_vote_2",
            "VoteCompletedEvent",
            "event:key:vote:2",
            "ARENA_BATTLE",
            "battle_2",
            "{\"battleId\":\"battle_2\",\"orderId\":\"order_2\",\"winner\":\"RIGHT\"}",
            Instant.parse("2026-02-28T12:00:00Z"));
    AchievementInboxEventJpaEntity clickEvent =
        AchievementInboxEventJpaEntity.of(
            "evt_click_2",
            "TickerItemClickedEvent",
            "event:key:click:2",
            "NARRATOR_TICKER",
            "u_6",
            "{\"eventId\":\"evt_ticker_2\",\"eventType\":\"ticker.rank_surge\",\"actionUrl\":\"/specimen/sp_1\"}",
            Instant.parse("2026-02-28T11:20:00Z"));

    when(arenaVoteLookupPort.findVoterUserIdByOrderId("order_2")).thenReturn(Optional.of("u_6"));
    when(inboxEventRepository.findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
            eq("TickerItemClickedEvent"), eq("u_6"), any()))
        .thenReturn(List.of(clickEvent));

    service.evaluateInboundEvent(voteEvent);

    verify(userAchievementRepository, never()).save(any(UserAchievementJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void evaluateVoteCompletedShouldSkipWhenVoteActorMissing() {
    AchievementInboxEventJpaEntity voteEvent =
        AchievementInboxEventJpaEntity.of(
            "evt_vote_3",
            "VoteCompletedEvent",
            "event:key:vote:3",
            "ARENA_BATTLE",
            "battle_3",
            "{\"battleId\":\"battle_3\",\"orderId\":\"order_3\"}",
            Instant.parse("2026-02-28T12:00:00Z"));

    when(arenaVoteLookupPort.findVoterUserIdByOrderId("order_3")).thenReturn(Optional.empty());

    service.evaluateInboundEvent(voteEvent);

    verify(userAchievementRepository, never()).save(any(UserAchievementJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void evaluateTickerItemClickShouldNotUnlockDirectly() {
    AchievementInboxEventJpaEntity event =
        AchievementInboxEventJpaEntity.of(
            "evt_3",
            "TickerItemClickedEvent",
            "event:key:3",
            "NARRATOR_TICKER",
            "u_3",
            "{\"userId\":\"u_3\",\"eventId\":\"evt_ticker_1\",\"eventType\":\"ticker.rank_surge\"}",
            Instant.parse("2026-02-28T11:00:00Z"));

    service.evaluateInboundEvent(event);

    verify(userAchievementRepository, never()).save(any(UserAchievementJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }
}
