package com.wtfrepo.backend.specimen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagAssignment;
import com.wtfrepo.backend.specimen.application.support.SpecimenJsonCodec;
import com.wtfrepo.backend.specimen.application.support.SpecimenRequestFingerprintCalculator;
import com.wtfrepo.backend.specimen.domain.SpecimenReviewAction;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenTagJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenAdminActionIdempotencyJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenCodeHighlightJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenGithubMetadataJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenOfficialCommentaryJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenReadmeExcerptJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenRepoIdentityJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenTagJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecimenAdminServiceOutboxTest {

  @Mock
  private SpecimenJpaRepository specimenJpaRepository;

  @Mock
  private SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository;

  @Mock
  private SpecimenRatingJpaRepository specimenRatingJpaRepository;

  @Mock
  private SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository;

  @Mock
  private SpecimenTagJpaRepository specimenTagJpaRepository;

  @Mock
  private SpecimenReadmeExcerptJpaRepository specimenReadmeExcerptJpaRepository;

  @Mock
  private SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository;

  @Mock
  private SpecimenCodeHighlightJpaRepository specimenCodeHighlightJpaRepository;

  @Mock
  private SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository;

  @Mock
  private SpecimenAdminActionIdempotencyJpaRepository specimenAdminActionIdempotencyJpaRepository;

  @Mock
  private ArenaRuntimePolicyPort arenaRuntimePolicyPort;

  @Mock
  private OutboxEventStore outboxEventStore;

  private SpecimenAdminService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    JsonUtils jsonUtils = new JsonUtils(objectMapper);
    SpecimenRequestFingerprintCalculator fingerprintCalculator =
        new SpecimenRequestFingerprintCalculator(jsonUtils);
    SpecimenJsonCodec specimenJsonCodec = new SpecimenJsonCodec(jsonUtils);
    SpecimenContractProperties specimenContractProperties = new SpecimenContractProperties();
    SpecimenOutboxEventPublisher specimenOutboxEventPublisher =
        new SpecimenOutboxEventPublisher(outboxEventStore);

    service =
        new SpecimenAdminService(
            specimenJpaRepository,
            specimenArenaMetricsJpaRepository,
            specimenRatingJpaRepository,
            specimenGithubMetadataJpaRepository,
            specimenTagJpaRepository,
            specimenReadmeExcerptJpaRepository,
            specimenOfficialCommentaryJpaRepository,
            specimenCodeHighlightJpaRepository,
            specimenRepoIdentityJpaRepository,
            specimenAdminActionIdempotencyJpaRepository,
            specimenContractProperties,
            arenaRuntimePolicyPort,
            fingerprintCalculator,
            specimenJsonCodec,
            specimenOutboxEventPublisher);

    when(specimenAdminActionIdempotencyJpaRepository.findById(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void reviewApproveShouldAppendSpecimenActivatedEvent() {
    String specimenId = "spm_active_1";
    SpecimenJpaEntity pendingSpecimen =
        SpecimenJpaEntity.createDraft(specimenId, "owner/repo", "https://github.com/owner/repo");
    pendingSpecimen.submit("ready");

    when(specimenJpaRepository.findById(specimenId)).thenReturn(Optional.of(pendingSpecimen));
    when(specimenTagJpaRepository.findBySpecimenId(specimenId))
        .thenReturn(
            List.of(
                SpecimenTagJpaEntity.of(specimenId, "species", "frontend"),
                SpecimenTagJpaEntity.of(specimenId, "diagnosis", "memory_leak"),
                SpecimenTagJpaEntity.of(specimenId, "diagnosis", "race_condition")));

    service.review("admin_1", specimenId, "idem-review-1", SpecimenReviewAction.APPROVE, "ok");

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("SpecimenActivatedEvent");
    assertThat(event.aggregateType()).isEqualTo("SPECIMEN");
    assertThat(event.aggregateId()).isEqualTo(specimenId);

    Map<String, Object> payload =
        objectMapper.convertValue(event.payload(), new TypeReference<Map<String, Object>>() {});
    assertThat(payload.get("specimenId")).isEqualTo(specimenId);
    assertThat(payload.get("species")).isEqualTo("frontend");
    assertThat(payload.get("status")).isEqualTo("ACTIVE");
    assertThat(payload.get("diagnosisTags"))
        .isEqualTo(List.of("memory_leak", "race_condition"));
  }

  @Test
  void deactivateShouldAppendSpecimenDeactivatedEvent() {
    String specimenId = "spm_active_2";
    SpecimenJpaEntity activeSpecimen =
        SpecimenJpaEntity.createDraft(specimenId, "owner/repo", "https://github.com/owner/repo");
    activeSpecimen.submit("ready");
    activeSpecimen.approve("reviewer_1");

    when(specimenJpaRepository.findById(specimenId)).thenReturn(Optional.of(activeSpecimen));

    service.deactivate("admin_1", specimenId, "idem-deactivate-1", "off-shelf");

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("SpecimenDeactivatedEvent");
    assertThat(event.aggregateType()).isEqualTo("SPECIMEN");
    assertThat(event.aggregateId()).isEqualTo(specimenId);

    Map<String, Object> payload =
        objectMapper.convertValue(event.payload(), new TypeReference<Map<String, Object>>() {});
    assertThat(payload.get("specimenId")).isEqualTo(specimenId);
  }

  @Test
  void updateTagsShouldAppendSpecimenTagsChangedEventWhenDiffDetected() {
    String specimenId = "spm_active_3";
    SpecimenJpaEntity activeSpecimen =
        SpecimenJpaEntity.createDraft(specimenId, "owner/repo", "https://github.com/owner/repo");
    activeSpecimen.submit("ready");
    activeSpecimen.approve("reviewer_1");

    when(specimenJpaRepository.findById(specimenId)).thenReturn(Optional.of(activeSpecimen));
    when(specimenTagJpaRepository.findBySpecimenId(specimenId))
        .thenReturn(
            List.of(
                SpecimenTagJpaEntity.of(specimenId, "species", "frontend"),
                SpecimenTagJpaEntity.of(specimenId, "diagnosis", "memory_leak")));

    service.updateTags(
        "admin_1",
        specimenId,
        "idem-tag-update-1",
        List.of(
            new TagAssignment("species", "backend"),
            new TagAssignment("diagnosis", "race_condition")));

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("SpecimenTagsChangedEvent");
    assertThat(event.aggregateType()).isEqualTo("SPECIMEN");
    assertThat(event.aggregateId()).isEqualTo(specimenId);

    Map<String, Object> payload =
        objectMapper.convertValue(event.payload(), new TypeReference<Map<String, Object>>() {});
    assertThat(payload.get("specimenId")).isEqualTo(specimenId);
    assertThat(payload.get("oldSpecies")).isEqualTo("frontend");
    assertThat(payload.get("newSpecies")).isEqualTo("backend");
    assertThat(payload.get("oldDiagnosisTags")).isEqualTo(List.of("memory_leak"));
    assertThat(payload.get("newDiagnosisTags")).isEqualTo(List.of("race_condition"));
  }

  @Test
  void updateTagsShouldSkipOutboxEventWhenTagSetUnchanged() {
    String specimenId = "spm_active_4";
    SpecimenJpaEntity activeSpecimen =
        SpecimenJpaEntity.createDraft(specimenId, "owner/repo", "https://github.com/owner/repo");
    activeSpecimen.submit("ready");
    activeSpecimen.approve("reviewer_1");

    when(specimenJpaRepository.findById(specimenId)).thenReturn(Optional.of(activeSpecimen));
    when(specimenTagJpaRepository.findBySpecimenId(specimenId))
        .thenReturn(
            List.of(
                SpecimenTagJpaEntity.of(specimenId, "species", "backend"),
                SpecimenTagJpaEntity.of(specimenId, "diagnosis", "race_condition")));

    service.updateTags(
        "admin_1",
        specimenId,
        "idem-tag-update-2",
        List.of(
            new TagAssignment("diagnosis", "race_condition"),
            new TagAssignment("species", "backend")));

    verify(outboxEventStore, never()).append(org.mockito.ArgumentMatchers.any());
  }
}
