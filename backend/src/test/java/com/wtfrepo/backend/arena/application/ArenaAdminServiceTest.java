package com.wtfrepo.backend.arena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.application.profile.ArenaMatchProfilePort;
import com.wtfrepo.backend.arena.application.profile.ArenaMatchProfileSnapshot;
import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicy;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArenaAdminServiceTest {

  @Mock
  private ArenaSpecimenMatchPairRebuildService matchPairRebuildService;

  @Mock
  private ArenaSpecimenMatchReadModel specimenMatchReadModel;

  @Mock
  private ArenaSpecimenMatchPairReadModel specimenMatchPairReadModel;

  @Mock
  private ArenaSpecimenRatingStore arenaSpecimenRatingStore;

  @Mock
  private SpecimenRatingJpaRepository specimenRatingJpaRepository;

  @Mock
  private ArenaRuntimePolicyPort arenaRuntimePolicyPort;

  private ArenaAdminService service;

  @BeforeEach
  void setUp() {
    ArenaMatchProperties arenaMatchProperties = new ArenaMatchProperties();
    arenaMatchProperties.setResetExcludeThreshold(4);
    ArenaMatchProfilePort matchProfilePort =
        () -> new ArenaMatchProfileSnapshot("v1.0.0", "species", "diagnosis");

    service =
        new ArenaAdminService(
            matchPairRebuildService,
            specimenMatchReadModel,
            specimenMatchPairReadModel,
            arenaSpecimenRatingStore,
            specimenRatingJpaRepository,
            arenaRuntimePolicyPort,
            arenaMatchProperties,
            matchProfilePort);
  }

  @Test
  void shouldForceRecalcAndReturnUpdatedQualityReport() {
    when(matchPairRebuildService.rebuildAllPairs(anyString()))
        .thenReturn(new ArenaSpecimenMatchPairRebuildService.PairRebuildResult(3, 2, 3));
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(
            List.of(
                candidate("spm_a"),
                candidate("spm_b"),
                candidate("spm_c"),
                candidate("spm_a")));
    when(specimenMatchPairReadModel.listActivePairs())
        .thenReturn(
            List.of(
                pair("spm_a", "spm_b", ArenaMatchType.SAME_SPECIES, 120, "v1.0.0"),
                pair("spm_a", "spm_c", ArenaMatchType.ADJACENT, 80, "v1.0.0"),
                pair("spm_b", "spm_c", ArenaMatchType.CROSS, 20, "v1.0.0")));

    ArenaAdminService.ForceRecalcResult result = service.forceRecalc("admin_1", "manual run");

    ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(matchPairRebuildService).rebuildAllPairs(reasonCaptor.capture());

    assertThat(reasonCaptor.getValue()).isEqualTo("ADMIN_FORCE_RECALC:admin_1:manual run");
    assertThat(result.activeSpecimens()).isEqualTo(3);
    assertThat(result.deletedPairs()).isEqualTo(2);
    assertThat(result.upsertedPairs()).isEqualTo(3);

    ArenaAdminService.MatchQualityReport report = result.matchQuality();
    assertThat(report.activeSpecimenCount()).isEqualTo(3);
    assertThat(report.expectedPairCount()).isEqualTo(3);
    assertThat(report.availablePairCount()).isEqualTo(3);
    assertThat(report.pairCoverageComplete()).isTrue();
    assertThat(report.pairCoverageRatio()).isEqualByComparingTo(new BigDecimal("1.0000"));
    assertThat(report.profileVersionAligned()).isTrue();
    assertThat(report.scoreSummary().averageScore()).isEqualByComparingTo(new BigDecimal("73.33"));
  }

  @Test
  void shouldResetEloForLowVoteActiveSpecimensOnly() {
    when(arenaRuntimePolicyPort.currentArenaRuntimePolicy())
        .thenReturn(new ArenaRuntimePolicy(Duration.ofMinutes(10), false, 1500));
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(List.of(candidate("spm_a"), candidate("spm_b")));

    SpecimenRatingJpaEntity lowVoteEntity =
        SpecimenRatingJpaEntity.createFromLegacyMetrics("spm_a", 1730, 3);
    SpecimenRatingJpaEntity highVoteEntity =
        SpecimenRatingJpaEntity.createFromLegacyMetrics("spm_b", 1680, 4);

    when(specimenRatingJpaRepository.findAllBySpecimenIdInForUpdate(org.mockito.ArgumentMatchers.<String>anyCollection()))
        .thenReturn(List.of(lowVoteEntity, highVoteEntity), List.of(lowVoteEntity, highVoteEntity));
    when(specimenMatchPairReadModel.listActivePairs()).thenReturn(List.of());

    ArenaAdminService.ResetEloResult result = service.resetElo("admin_1", "incident-recover");

    assertThat(result.resetReason()).isEqualTo("ADMIN_RESET_ELO:admin_1:incident-recover");
    assertThat(result.targetElo()).isEqualTo(1500);
    assertThat(result.resetExcludeThreshold()).isEqualTo(4);
    assertThat(result.activeSpecimens()).isEqualTo(2);
    assertThat(result.lockedSpecimens()).isEqualTo(2);
    assertThat(result.resetCandidates()).isEqualTo(1);
    assertThat(result.updatedSpecimens()).isEqualTo(1);
    assertThat(result.excludedSpecimens()).isEqualTo(1);
    assertThat(result.missingSpecimens()).isEqualTo(0);

    assertThat(lowVoteEntity.getEloScore()).isEqualTo(1500);
    assertThat(lowVoteEntity.getEloOpenToday()).isEqualTo(1500);
    assertThat(highVoteEntity.getEloScore()).isEqualTo(1680);
    assertThat(highVoteEntity.getEloOpenToday()).isEqualTo(1680);

    verify(arenaSpecimenRatingStore, never()).findForUpdate(anyString());
    verify(specimenRatingJpaRepository).saveAll(List.of(lowVoteEntity, highVoteEntity));
  }

  @Test
  void shouldReportCoverageAndProfileVersionDrift() {
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(List.of(candidate("spm_a"), candidate("spm_b"), candidate("spm_c"), candidate("spm_d")));
    when(specimenMatchPairReadModel.listActivePairs())
        .thenReturn(
            List.of(
                pair("spm_a", "spm_b", ArenaMatchType.SAME_SPECIES, 100, "v1.0.0"),
                pair("spm_a", "spm_c", ArenaMatchType.ADJACENT, 90, "v2.0.0"),
                pair("spm_b", "spm_c", ArenaMatchType.CROSS, 40, "v2.0.0")));

    ArenaAdminService.MatchQualityReport report = service.evaluateMatchQuality();

    assertThat(report.activeSpecimenCount()).isEqualTo(4);
    assertThat(report.expectedPairCount()).isEqualTo(6);
    assertThat(report.availablePairCount()).isEqualTo(3);
    assertThat(report.pairCoverageComplete()).isFalse();
    assertThat(report.pairCoverageRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
    assertThat(report.profileVersionAligned()).isFalse();

    assertThat(report.matchTypeBreakdown())
        .extracting(ArenaAdminService.MatchTypeBreakdown::pairCount)
        .containsExactly(1, 1, 1);
    assertThat(report.profileVersionBreakdown())
        .extracting(ArenaAdminService.ProfileVersionBreakdown::profileVersion)
        .containsExactly("v2.0.0", "v1.0.0");
  }

  private ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate(String specimenId) {
    return new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
        specimenId,
        "title",
        "tagline",
        "thumbnail",
        "species",
        List.of("tag"));
  }

  private ArenaSpecimenMatchPairReadModel.SpecimenMatchPair pair(
      String leftSpecimenId,
      String rightSpecimenId,
      ArenaMatchType matchType,
      int matchScore,
      String profileVersion) {
    return new ArenaSpecimenMatchPairReadModel.SpecimenMatchPair(
        leftSpecimenId, rightSpecimenId, matchType, matchScore, profileVersion);
  }
}
