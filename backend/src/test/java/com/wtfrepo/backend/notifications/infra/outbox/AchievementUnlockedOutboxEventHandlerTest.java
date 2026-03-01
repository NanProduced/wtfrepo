package com.wtfrepo.backend.notifications.infra.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.notifications.application.NotificationOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementUnlockedOutboxEventHandlerTest {

  @Mock
  private NotificationOutboxEventConsumerService consumerService;

  private NotificationOutboxPayloadReader payloadReader;

  @BeforeEach
  void setUp() {
    payloadReader = new NotificationOutboxPayloadReader(new ObjectMapper());
  }

  @Test
  void handleShouldDelegateWhenPayloadValid() {
    AchievementUnlockedOutboxEventHandler handler =
        new AchievementUnlockedOutboxEventHandler(payloadReader, consumerService);
    Instant occurredAt = Instant.parse("2026-02-28T10:10:00Z");

    handler.handle(
        message(
            "evt-ach-notify-1",
            occurredAt,
            "{\"userId\":\"u_9\",\"achievementCode\":\"ACH_TICKER_SCALPER\",\"displayName\":\"Ticker Scalper\",\"rewardBug\":120}"));

    verify(consumerService)
        .onAchievementUnlocked(
            eq("evt-ach-notify-1"),
            eq("u_9"),
            eq("ACH_TICKER_SCALPER"),
            eq("Ticker Scalper"),
            eq(120),
            eq(occurredAt));
  }

  @Test
  void handleShouldSkipWhenRequiredFieldMissing() {
    AchievementUnlockedOutboxEventHandler handler =
        new AchievementUnlockedOutboxEventHandler(payloadReader, consumerService);

    handler.handle(
        message(
            "evt-ach-notify-2",
            Instant.parse("2026-02-28T10:11:00Z"),
            "{\"achievementCode\":\"ACH_TICKER_SCALPER\",\"rewardBug\":120}"));

    verify(consumerService, never())
        .onAchievementUnlocked(any(), any(), any(), any(), any(), any());
  }

  private OutboxStreamMessage message(String eventId, Instant occurredAt, String payload) {
    return new OutboxStreamMessage(
        "174-0",
        "wtfrepo:outbox:events",
        eventId,
        "USER_ACHIEVEMENT",
        "u_9",
        "AchievementUnlockedEvent",
        "event:key",
        payload,
        occurredAt,
        Instant.now(),
        Instant.now(),
        Map.of("eventId", eventId, "eventType", "AchievementUnlockedEvent"));
  }
}
