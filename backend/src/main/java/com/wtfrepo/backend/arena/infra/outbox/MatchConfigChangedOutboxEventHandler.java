package com.wtfrepo.backend.arena.infra.outbox;

import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchPairRebuildService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles M04 {@code MatchConfigChangedEvent} for full pair rebuild. */
@Component
public class MatchConfigChangedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log = LoggerFactory.getLogger(MatchConfigChangedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "MatchConfigChangedEvent";
  private static final Set<String> SUPPORTED_CONFIG_TYPES =
      Set.of("MATCH_PROFILE", "SPECIES_ADJACENCY");

  private final ArenaOutboxPayloadReader payloadReader;
  private final ArenaSpecimenMatchPairRebuildService rebuildService;

  public MatchConfigChangedOutboxEventHandler(
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
    String configType =
        payloadReader.readEnumLikeUppercaseField(message.payload(), "configType").orElse(null);
    if (configType == null) {
      log.warn(
          "arena_match_pair_event_payload_invalid eventType={} eventId={} reason=missing_config_type",
          EVENT_TYPE,
          message.eventId());
      return;
    }

    if (!SUPPORTED_CONFIG_TYPES.contains(configType)) {
      log.info(
          "arena_match_pair_config_change_skip eventId={} configType={} reason=unsupported_type",
          message.eventId(),
          configType);
      return;
    }

    String version = payloadReader.readTextField(message.payload(), "version").orElse("unknown");
    String profileVersionOverride = "MATCH_PROFILE".equals(configType) ? version : null;
    rebuildService.rebuildAllPairs(
        EVENT_TYPE + ":" + configType + ":" + version, profileVersionOverride);
  }
}
