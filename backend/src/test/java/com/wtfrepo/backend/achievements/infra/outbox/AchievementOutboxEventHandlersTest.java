package com.wtfrepo.backend.achievements.infra.outbox;

import static org.mockito.Mockito.verify;

import com.wtfrepo.backend.achievements.application.AchievementInboxService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementOutboxEventHandlersTest {

  @Mock
  private AchievementInboxService inboxService;

  @Test
  void voteCompletedHandlerShouldForwardMessageToInboxService() {
    VoteCompletedOutboxEventHandler handler = new VoteCompletedOutboxEventHandler(inboxService);
    OutboxStreamMessage message = sampleMessage("VoteCompletedEvent", "evt_vote_1");

    handler.handle(message);

    verify(inboxService).recordVoteCompleted(message);
  }

  @Test
  void watchlistAddedHandlerShouldForwardMessageToInboxService() {
    WatchlistAddedOutboxEventHandler handler = new WatchlistAddedOutboxEventHandler(inboxService);
    OutboxStreamMessage message = sampleMessage("WatchlistAddedEvent", "evt_watch_1");

    handler.handle(message);

    verify(inboxService).recordWatchlistAdded(message);
  }

  @Test
  void hypeParticipatedHandlerShouldForwardMessageToInboxService() {
    HypeParticipatedOutboxEventHandler handler =
        new HypeParticipatedOutboxEventHandler(inboxService);
    OutboxStreamMessage message = sampleMessage("HypeParticipatedEvent", "evt_hype_1");

    handler.handle(message);

    verify(inboxService).recordHypeParticipated(message);
  }

  @Test
  void narratorPreferenceChangedHandlerShouldForwardMessageToInboxService() {
    NarratorPreferenceChangedOutboxEventHandler handler =
        new NarratorPreferenceChangedOutboxEventHandler(inboxService);
    OutboxStreamMessage message = sampleMessage("NarratorPreferenceChangedEvent", "evt_narrator_1");

    handler.handle(message);

    verify(inboxService).recordNarratorPreferenceChanged(message);
  }

  @Test
  void tickerItemClickedHandlerShouldForwardMessageToInboxService() {
    TickerItemClickedOutboxEventHandler handler =
        new TickerItemClickedOutboxEventHandler(inboxService);
    OutboxStreamMessage message = sampleMessage("TickerItemClickedEvent", "evt_ticker_click_1");

    handler.handle(message);

    verify(inboxService).recordTickerItemClicked(message);
  }

  private OutboxStreamMessage sampleMessage(String eventType, String eventId) {
    return new OutboxStreamMessage(
        "173-0",
        "wtfrepo:outbox:events",
        eventId,
        "SPECIMEN",
        "sp_1",
        eventType,
        "event:key",
        "{\"userId\":\"u_1\"}",
        Instant.now(),
        Instant.now(),
        Instant.now(),
        Map.of("eventId", eventId, "eventType", eventType));
  }
}
