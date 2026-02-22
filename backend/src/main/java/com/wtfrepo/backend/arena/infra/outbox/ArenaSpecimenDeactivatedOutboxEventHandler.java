package com.wtfrepo.backend.arena.infra.outbox;

import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchPairRebuildService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles M04 {@code SpecimenDeactivatedEvent} for pair cleanup. */
@Component
public class ArenaSpecimenDeactivatedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(ArenaSpecimenDeactivatedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "SpecimenDeactivatedEvent";

  private final ArenaOutboxPayloadReader payloadReader;
  private final ArenaSpecimenMatchPairRebuildService rebuildService;

  public ArenaSpecimenDeactivatedOutboxEventHandler(
      ArenaOutboxPayloadReader payloadReader,
      ArenaSpecimenMatchPairRebuildService rebuildService) {
    this.payloadReader = payloadReader;
    this.rebuildService = rebuildService;
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
          "arena_match_pair_event_payload_invalid eventType={} eventId={} reason=missing_specimen_id",
          EVENT_TYPE,
          message.eventId());
      return;
    }

    rebuildService.removePairsForSpecimen(specimenId, EVENT_TYPE);
  }
}
