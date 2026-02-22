package com.wtfrepo.backend.economy.infra.outbox;

import com.wtfrepo.backend.economy.application.BettingOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles Arena {@code DailySnapshotCreatedEvent} for betting audit/cache refresh hooks.
 *
 * <p>This event does not trigger settlement directly in current architecture.
 */
@Component
public class DailySnapshotCreatedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(DailySnapshotCreatedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "DailySnapshotCreatedEvent";

  private final EconomyOutboxPayloadReader payloadReader;
  private final BettingOutboxEventConsumerService eventConsumerService;

  public DailySnapshotCreatedOutboxEventHandler(
      EconomyOutboxPayloadReader payloadReader,
      BettingOutboxEventConsumerService eventConsumerService) {
    this.payloadReader = payloadReader;
    this.eventConsumerService = eventConsumerService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String specimenId = payloadReader.readTextField(message.payload(), "specimenId").orElse(null);
    LocalDate date = payloadReader.readLocalDateField(message.payload(), "date").orElse(null);
    if (specimenId == null || date == null) {
      log.warn(
          "betting_outbox_payload_invalid eventType={} eventId={} reason=missing_required_fields",
          EVENT_TYPE,
          message.eventId());
      return;
    }

    Integer eloOpen = payloadReader.readIntField(message.payload(), "eloOpen").orElse(null);
    Integer eloClose = payloadReader.readIntField(message.payload(), "eloClose").orElse(null);
    Integer deltaR = payloadReader.readIntField(message.payload(), "deltaR").orElse(null);
    eventConsumerService.onDailySnapshotCreated(
        message.eventId(), specimenId, date, eloOpen, eloClose, deltaR, message.occurredAt());
  }
}
