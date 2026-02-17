package com.wtfrepo.backend.specimen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecimenMatchConfigPublishServiceTest {

  @Mock
  private OutboxEventStore outboxEventStore;

  private SpecimenMatchConfigPublishService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    SpecimenOutboxEventPublisher publisher = new SpecimenOutboxEventPublisher(outboxEventStore);
    service = new SpecimenMatchConfigPublishService(publisher);
  }

  @Test
  void publishMatchProfileChangedShouldAppendOutboxEvent() {
    service.publishMatchProfileChanged("idem-cfg-1", "profile_v2026_02_16");

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("MatchConfigChangedEvent");
    assertThat(event.aggregateType()).isEqualTo("SPECIMEN_CONFIG");
    assertThat(event.aggregateId()).isEqualTo("MATCH_PROFILE");

    Map<String, Object> payload =
        objectMapper.convertValue(event.payload(), new TypeReference<Map<String, Object>>() {});
    assertThat(payload.get("configType")).isEqualTo("MATCH_PROFILE");
    assertThat(payload.get("version")).isEqualTo("profile_v2026_02_16");
  }

  @Test
  void publishShouldRejectUnsupportedConfigType() {
    assertThatThrownBy(() -> service.publish("UNSUPPORTED", "idem-cfg-2", "v1"))
        .hasMessageContaining("invalid_match_config_type");
  }
}
