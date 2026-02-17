package com.wtfrepo.backend.arena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenMatchPairJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenMatchPairJpaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArenaSpecimenMatchPairRebuildServiceTest {

  @Mock
  private ArenaSpecimenMatchReadModel specimenMatchReadModel;

  @Mock
  private SpecimenMatchPairJpaRepository specimenMatchPairJpaRepository;

  private ArenaSpecimenMatchPairRebuildService service;

  @BeforeEach
  void setUp() {
    ArenaMatchProperties arenaMatchProperties = new ArenaMatchProperties();
    arenaMatchProperties.setAdjacentSpeciesPairs(List.of("cat:dog"));
    service =
        new ArenaSpecimenMatchPairRebuildService(
            specimenMatchReadModel, specimenMatchPairJpaRepository, arenaMatchProperties);
  }

  @Test
  void shouldRebuildAllPairsFromCurrentActiveCandidates() {
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(
            List.of(
                candidate("spm_a", "dog", List.of("d1", "d2")),
                candidate("spm_b", "dog", List.of("d2", "d3")),
                candidate("spm_c", "cat", List.of("d2", "d3"))));
    when(specimenMatchPairJpaRepository.count()).thenReturn(2L);

    ArenaSpecimenMatchPairRebuildService.PairRebuildResult result =
        service.rebuildAllPairs("unit-test");

    verify(specimenMatchPairJpaRepository).deleteAllInBatch();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SpecimenMatchPairJpaEntity>> pairsCaptor = ArgumentCaptor.forClass(List.class);
    verify(specimenMatchPairJpaRepository).saveAll(pairsCaptor.capture());

    Map<String, SpecimenMatchPairJpaEntity> pairsByKey =
        toPairMapBySpecimenIds(pairsCaptor.getValue());
    assertThat(pairsByKey).hasSize(3);

    assertPair(pairsByKey.get("spm_a:spm_b"), ArenaMatchType.SAME_SPECIES, 115);
    assertPair(pairsByKey.get("spm_a:spm_c"), ArenaMatchType.ADJACENT, 75);
    assertPair(pairsByKey.get("spm_b:spm_c"), ArenaMatchType.ADJACENT, 90);

    assertThat(result.activeSpecimens()).isEqualTo(3);
    assertThat(result.deletedPairs()).isEqualTo(2);
    assertThat(result.upsertedPairs()).isEqualTo(3);
  }

  @Test
  void shouldIncrementallyRebuildPairsForSingleSpecimen() {
    when(specimenMatchPairJpaRepository.deleteAllBySpecimenId("spm_b")).thenReturn(5);
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(
            List.of(
                candidate("spm_a", "cat", List.of("d2")),
                candidate("spm_b", "dog", List.of("d1", "d2")),
                candidate("spm_c", "dog", List.of("d2", "d3"))));

    ArenaSpecimenMatchPairRebuildService.PairRebuildResult result =
        service.rebuildPairsForSpecimen("spm_b", "SpecimenActivatedEvent");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SpecimenMatchPairJpaEntity>> pairsCaptor = ArgumentCaptor.forClass(List.class);
    verify(specimenMatchPairJpaRepository).saveAll(pairsCaptor.capture());

    Map<String, SpecimenMatchPairJpaEntity> pairsByKey =
        toPairMapBySpecimenIds(pairsCaptor.getValue());
    assertThat(pairsByKey).hasSize(2);
    assertPair(pairsByKey.get("spm_a:spm_b"), ArenaMatchType.ADJACENT, 75);
    assertPair(pairsByKey.get("spm_b:spm_c"), ArenaMatchType.SAME_SPECIES, 115);

    assertThat(result.activeSpecimens()).isEqualTo(3);
    assertThat(result.deletedPairs()).isEqualTo(5);
    assertThat(result.upsertedPairs()).isEqualTo(2);
  }

  @Test
  void shouldSkipIncrementalInsertWhenSpecimenIsNoLongerActive() {
    when(specimenMatchPairJpaRepository.deleteAllBySpecimenId("spm_missing")).thenReturn(3);
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(List.of(candidate("spm_a", "cat", List.of("d2"))));

    ArenaSpecimenMatchPairRebuildService.PairRebuildResult result =
        service.rebuildPairsForSpecimen("spm_missing", "SpecimenTagsChangedEvent");

    verify(specimenMatchPairJpaRepository, never()).saveAll(anyList());
    assertThat(result.activeSpecimens()).isEqualTo(1);
    assertThat(result.deletedPairs()).isEqualTo(3);
    assertThat(result.upsertedPairs()).isEqualTo(0);
  }

  @Test
  void shouldRemovePairsForSpecimen() {
    when(specimenMatchPairJpaRepository.deleteAllBySpecimenId("spm_x")).thenReturn(2);

    int deleted = service.removePairsForSpecimen("spm_x", "SpecimenDeactivatedEvent");

    verify(specimenMatchPairJpaRepository).deleteAllBySpecimenId(eq("spm_x"));
    assertThat(deleted).isEqualTo(2);
  }

  private Map<String, SpecimenMatchPairJpaEntity> toPairMapBySpecimenIds(
      List<SpecimenMatchPairJpaEntity> pairs) {
    Map<String, SpecimenMatchPairJpaEntity> mapping = new LinkedHashMap<>();
    for (SpecimenMatchPairJpaEntity pair : pairs) {
      mapping.put(pair.getLeftSpecimenId() + ":" + pair.getRightSpecimenId(), pair);
    }
    return mapping;
  }

  private void assertPair(SpecimenMatchPairJpaEntity pair, ArenaMatchType matchType, int matchScore) {
    assertThat(pair).isNotNull();
    assertThat(pair.getMatchType()).isEqualTo(matchType);
    assertThat(pair.getMatchScore()).isEqualTo(matchScore);
    assertThat(pair.getMatchProfileVersion()).isEqualTo("v1.0.0");
    assertThat(pair.getComputedAt()).isNotNull();
  }

  private ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate(
      String specimenId, String species, List<String> diagnosisTags) {
    return new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
        specimenId,
        "title",
        "tagline",
        "thumbnail",
        species,
        diagnosisTags);
  }
}
