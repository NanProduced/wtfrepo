package com.wtfrepo.backend.arena.infra.outbox;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchPairRebuildService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArenaOutboxEventHandlersTest {

  @Mock
  private ArenaSpecimenMatchPairRebuildService rebuildService;

  private ArenaOutboxPayloadReader payloadReader;

  @BeforeEach
  void setUp() {
    payloadReader = new ArenaOutboxPayloadReader(new ObjectMapper());
  }

  @Test
  void specimenActivatedShouldTriggerIncrementalRebuild() {
    SpecimenActivatedOutboxEventHandler handler =
        new SpecimenActivatedOutboxEventHandler(payloadReader, rebuildService);

    handler.handle(message("evt-1", "{\"specimenId\":\"spm_1\"}"));

    verify(rebuildService).rebuildPairsForSpecimen("spm_1", "SpecimenActivatedEvent");
  }

  @Test
  void specimenDeactivatedShouldTriggerPairRemoval() {
    SpecimenDeactivatedOutboxEventHandler handler =
        new SpecimenDeactivatedOutboxEventHandler(payloadReader, rebuildService);

    handler.handle(message("evt-2", "{\"specimenId\":\"spm_2\"}"));

    verify(rebuildService).removePairsForSpecimen("spm_2", "SpecimenDeactivatedEvent");
  }

  @Test
  void specimenTagsChangedShouldIgnoreInvalidPayload() {
    SpecimenTagsChangedOutboxEventHandler handler =
        new SpecimenTagsChangedOutboxEventHandler(payloadReader, rebuildService);

    handler.handle(message("evt-3", "{}"));

    verify(rebuildService, never())
        .rebuildPairsForSpecimen(anyString(), eq("SpecimenTagsChangedEvent"));
  }

  @Test
  void matchConfigChangedShouldTriggerFullRebuildOnSupportedType() {
    MatchConfigChangedOutboxEventHandler handler =
        new MatchConfigChangedOutboxEventHandler(payloadReader, rebuildService);

    handler.handle(
        message(
            "evt-4",
            "{\"configType\":\"MATCH_PROFILE\",\"version\":\"profile_v2026_02_16\"}"));

    verify(rebuildService)
        .rebuildAllPairs("MatchConfigChangedEvent:MATCH_PROFILE:profile_v2026_02_16");
  }

  @Test
  void matchConfigChangedShouldSkipUnsupportedType() {
    MatchConfigChangedOutboxEventHandler handler =
        new MatchConfigChangedOutboxEventHandler(payloadReader, rebuildService);

    handler.handle(message("evt-5", "{\"configType\":\"UNRELATED\"}"));

    verify(rebuildService, never()).rebuildAllPairs(anyString());
  }

  private OutboxStreamMessage message(String eventId, String payload) {
    return new OutboxStreamMessage(
        "171111-0",
        "wtfrepo:outbox:events",
        eventId,
        "SPECIMEN",
        "specimen-1",
        "test-event",
        "event-key",
        payload,
        Instant.now(),
        Instant.now(),
        Instant.now(),
        Map.of());
  }
}
