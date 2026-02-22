package com.wtfrepo.backend.economy.infra.outbox;

import com.wtfrepo.backend.economy.application.BettingOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles Arena {@code IpoCompletedEvent} for betting pool initialization. */
@Component
public class IpoCompletedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log = LoggerFactory.getLogger(IpoCompletedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "IpoCompletedEvent";

  private final EconomyOutboxPayloadReader payloadReader;
  private final BettingOutboxEventConsumerService eventConsumerService;

  public IpoCompletedOutboxEventHandler(
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
    if (specimenId == null) {
      log.warn(
          "betting_outbox_payload_invalid eventType={} eventId={} reason=missing_specimen_id",
          EVENT_TYPE,
          message.eventId());
      return;
    }

    Integer calibratedScore = payloadReader.readIntField(message.payload(), "calibratedScore").orElse(null);
    eventConsumerService.onIpoCompleted(
        message.eventId(), specimenId, calibratedScore, message.occurredAt());
  }
}
