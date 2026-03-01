package com.wtfrepo.backend.specimen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.specimen.application.SpecimenHypeService.HypeParticipationResult;
import com.wtfrepo.backend.specimen.application.SpecimenOutboxEventPublisher.HypeParticipatedEventPayload;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecimenHypeServiceTest {

  @Mock
  private SpecimenJpaRepository specimenJpaRepository;

  @Mock
  private OutboxEventStore outboxEventStore;

  private SpecimenHypeService service;

  @BeforeEach
  void setUp() {
    SpecimenOutboxEventPublisher publisher = new SpecimenOutboxEventPublisher(outboxEventStore);
    service = new SpecimenHypeService(specimenJpaRepository, publisher);
  }

  @Test
  void participateShouldAppendHypeParticipatedEvent() {
    String userId = "u_1";
    String specimenId = "sp_1";
    Instant clientTs = Instant.parse("2026-02-28T10:00:00Z");

    when(specimenJpaRepository.existsById(specimenId)).thenReturn(true);

    HypeParticipationResult result =
        service.participate(userId, specimenId, "funny", clientTs, "idem_hype_1");

    assertThat(result.hypeScore()).isEqualTo(0.0D);
    assertThat(result.scoreDelta()).isEqualTo(0.0D);
    assertThat(result.appliedWeight()).isEqualTo(0.0D);

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("HypeParticipatedEvent");
    assertThat(event.aggregateType()).isEqualTo("SPECIMEN");
    assertThat(event.aggregateId()).isEqualTo(specimenId);

    assertThat(event.payload()).isInstanceOf(HypeParticipatedEventPayload.class);
    HypeParticipatedEventPayload payload = (HypeParticipatedEventPayload) event.payload();
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.specimenId()).isEqualTo(specimenId);
    assertThat(payload.dimension()).isEqualTo("FUNNY");
    assertThat(payload.idempotencyKey()).isEqualTo("idem_hype_1");
    assertThat(payload.participatedAt()).isNotNull();
    assertThat(payload.clientTs()).isEqualTo(clientTs);
  }

  @Test
  void participateShouldFailWhenSpecimenNotFound() {
    when(specimenJpaRepository.existsById("sp_missing")).thenReturn(false);

    assertThatThrownBy(
            () -> service.participate("u_2", "sp_missing", "FUNNY", Instant.now(), "idem_hype_2"))
        .hasMessage("specimen_not_found");
  }
}
