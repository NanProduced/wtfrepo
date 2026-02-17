package com.wtfrepo.backend.arena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import java.math.BigDecimal;
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

  private ArenaAdminService service;

  @BeforeEach
  void setUp() {
    ArenaMatchProperties arenaMatchProperties = new ArenaMatchProperties();
    arenaMatchProperties.setProfileVersion("v1.0.0");
    arenaMatchProperties.setResetExcludeThreshold(4);

    service =
        new ArenaAdminService(
            matchPairRebuildService,
            specimenMatchReadModel,
            specimenMatchPairReadModel,
            arenaMatchProperties);
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
