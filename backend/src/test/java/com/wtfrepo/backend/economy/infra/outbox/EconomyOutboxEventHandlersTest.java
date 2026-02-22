package com.wtfrepo.backend.economy.infra.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.economy.application.BettingOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EconomyOutboxEventHandlersTest {

  @Mock
  private BettingOutboxEventConsumerService eventConsumerService;

  private EconomyOutboxPayloadReader payloadReader;

  @BeforeEach
  void setUp() {
    payloadReader = new EconomyOutboxPayloadReader(new ObjectMapper());
  }

  @Test
  void ipoCompletedShouldDelegateToConsumerService() {
    IpoCompletedOutboxEventHandler handler =
        new IpoCompletedOutboxEventHandler(payloadReader, eventConsumerService);

    Instant occurredAt = Instant.parse("2026-02-18T00:00:00Z");
    handler.handle(
        message(
            "evt-ipo-1",
            occurredAt,
            "{\"specimenId\":\"spm_1\",\"calibratedScore\":1532,\"matchesPlayed\":10}"));

    verify(eventConsumerService)
        .onIpoCompleted(eq("evt-ipo-1"), eq("spm_1"), eq(1532), eq(occurredAt));
  }

  @Test
  void ipoCompletedShouldIgnoreMissingSpecimenId() {
    IpoCompletedOutboxEventHandler handler =
        new IpoCompletedOutboxEventHandler(payloadReader, eventConsumerService);

    handler.handle(message("evt-ipo-2", Instant.now(), "{\"calibratedScore\":1532}"));

    verify(eventConsumerService, never())
        .onIpoCompleted(any(), any(), any(), any());
  }

  @Test
  void specimenDeactivatedShouldDelegateToConsumerService() {
    EconomySpecimenDeactivatedOutboxEventHandler handler =
        new EconomySpecimenDeactivatedOutboxEventHandler(payloadReader, eventConsumerService);

    Instant occurredAt = Instant.parse("2026-02-18T01:00:00Z");
    handler.handle(
        message("evt-deact-1", occurredAt, "{\"specimenId\":\"spm_deactivated_1\"}"));

    verify(eventConsumerService)
        .onSpecimenDeactivated(eq("evt-deact-1"), eq("spm_deactivated_1"), eq(occurredAt));
  }

  @Test
  void dailySnapshotCreatedShouldDelegateToConsumerService() {
    DailySnapshotCreatedOutboxEventHandler handler =
        new DailySnapshotCreatedOutboxEventHandler(payloadReader, eventConsumerService);

    Instant occurredAt = Instant.parse("2026-02-18T02:00:00Z");
    handler.handle(
        message(
            "evt-snapshot-1",
            occurredAt,
            "{\"specimenId\":\"spm_2\",\"date\":\"2026-02-17\",\"eloOpen\":1520,\"eloClose\":1535,\"deltaR\":15}"));

    verify(eventConsumerService)
        .onDailySnapshotCreated(
            eq("evt-snapshot-1"),
            eq("spm_2"),
            eq(LocalDate.parse("2026-02-17")),
            eq(1520),
            eq(1535),
            eq(15),
            eq(occurredAt));
  }

  @Test
  void dailySnapshotCreatedShouldIgnoreInvalidDate() {
    DailySnapshotCreatedOutboxEventHandler handler =
        new DailySnapshotCreatedOutboxEventHandler(payloadReader, eventConsumerService);

    handler.handle(
        message(
            "evt-snapshot-2",
            Instant.now(),
            "{\"specimenId\":\"spm_2\",\"date\":\"invalid\"}"));

    verify(eventConsumerService, never())
        .onDailySnapshotCreated(any(), any(), any(), any(), any(), any(), any());
  }

  private OutboxStreamMessage message(String eventId, Instant occurredAt, String payload) {
    return new OutboxStreamMessage(
        "171111-0",
        "wtfrepo:outbox:events",
        eventId,
        "ARENA_SPECIMEN",
        "specimen-1",
        "test-event",
        "event-key",
        payload,
        occurredAt,
        Instant.now(),
        Instant.now(),
        Map.of());
  }
}
