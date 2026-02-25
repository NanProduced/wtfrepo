package com.wtfrepo.backend.specimen.infra.outbox;

import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import com.wtfrepo.backend.specimen.application.SpecimenArenaMetricsEventConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles arena Elo updates for specimen read models. */
@Component
public class EloUpdatedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(EloUpdatedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "EloUpdatedEvent";

  private final SpecimenOutboxPayloadReader payloadReader;
  private final SpecimenArenaMetricsEventConsumerService metricsService;

  public EloUpdatedOutboxEventHandler(
      SpecimenOutboxPayloadReader payloadReader,
      SpecimenArenaMetricsEventConsumerService metricsService) {
    this.payloadReader = payloadReader;
    this.metricsService = metricsService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String specimenId =
        payloadReader.readTextField(message.payload(), "specimenId").orElse(null);
    Integer eloBefore =
        payloadReader.readIntField(message.payload(), "eloBefore").orElse(null);
    Integer eloAfter =
        payloadReader.readIntField(message.payload(), "eloAfter").orElse(null);

    if (specimenId == null || eloBefore == null || eloAfter == null) {
      log.warn(
          "specimen_elo_updated_event_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    metricsService.onEloUpdated(message.eventId(), specimenId, eloBefore, eloAfter);
  }
}
