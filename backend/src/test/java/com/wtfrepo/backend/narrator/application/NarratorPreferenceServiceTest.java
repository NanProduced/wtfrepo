package com.wtfrepo.backend.narrator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.narrator.application.NarratorOutboxEventPublisher.NarratorPreferenceChangedPayload;
import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.narrator.infra.persistence.entity.NarratorPreferenceJpaEntity;
import com.wtfrepo.backend.narrator.infra.persistence.repository.NarratorPreferenceJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NarratorPreferenceServiceTest {

  @Mock
  private NarratorPreferenceJpaRepository preferenceRepository;

  @Mock
  private OutboxEventStore outboxEventStore;

  private NarratorPreferenceService service;

  @BeforeEach
  void setUp() {
    NarratorOutboxEventPublisher publisher = new NarratorOutboxEventPublisher(outboxEventStore);
    service = new NarratorPreferenceService(preferenceRepository, publisher);
  }

  @Test
  void patchPreferenceShouldPersistAndPublishOutbox() {
    String userId = "u_1";
    when(preferenceRepository.findById(userId)).thenReturn(Optional.empty());
    when(preferenceRepository.save(any(NarratorPreferenceJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NarratorModels.PreferenceView result =
        service.patchPreference(
            userId,
            new NarratorModels.PatchPreferenceCommand("LITE", "EDGY", false, true),
            "idem_pref_1");

    assertThat(result.mode()).isEqualTo("LITE");
    assertThat(result.modeIsExplicit()).isTrue();
    assertThat(result.tonePreference()).isEqualTo("EDGY");
    assertThat(result.eyeFollowEnabled()).isFalse();
    assertThat(result.tickerEnabled()).isTrue();

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("NarratorPreferenceChangedEvent");
    assertThat(event.aggregateType()).isEqualTo("NARRATOR_PREFERENCE");
    assertThat(event.aggregateId()).isEqualTo(userId);

    assertThat(event.payload()).isInstanceOf(NarratorPreferenceChangedPayload.class);
    NarratorPreferenceChangedPayload payload = (NarratorPreferenceChangedPayload) event.payload();
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.oldMode()).isEqualTo("FULL");
    assertThat(payload.newMode()).isEqualTo("LITE");
    assertThat(payload.tonePreference()).isEqualTo("EDGY");
  }

  @Test
  void mergeOnLoginShouldKeepServerValuesWhenExplicitFlagsPresent() {
    String userId = "u_2";
    NarratorPreferenceJpaEntity existing = NarratorPreferenceJpaEntity.createDefault(userId);
    existing.applyMode(com.wtfrepo.backend.narrator.domain.NarratorMode.OFF, true);
    existing.applyTone(com.wtfrepo.backend.narrator.domain.NarratorTone.EDGY, true);
    when(preferenceRepository.findById(userId)).thenReturn(Optional.of(existing));

    NarratorModels.MergeOnLoginResult result =
        service.mergeOnLogin(
            userId,
            new NarratorModels.MergeOnLoginCommand("FULL", "SAFE", null, null),
            "idem_merge_1");

    assertThat(result.mode()).isEqualTo("OFF");
    assertThat(result.tonePreference()).isEqualTo("EDGY");
    assertThat(result.mergedFrom()).isEqualTo("SERVER");
    verify(preferenceRepository, never()).save(any(NarratorPreferenceJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void patchPreferenceShouldRejectInvalidMode() {
    assertThatThrownBy(
            () ->
                service.patchPreference(
                    "u_3",
                    new NarratorModels.PatchPreferenceCommand("INVALID", null, null, null),
                    "idem_pref_2"))
        .hasMessage("invalid_mode");
  }

  @Test
  void patchPreferenceShouldRejectNullPayload() {
    assertThatThrownBy(() -> service.patchPreference("u_4", null, "idem_pref_3"))
        .hasMessage("invalid_payload");
  }
}
