package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.application.profile.ArenaMatchProfilePort;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Arena admin operations for match maintenance and quality diagnostics.
 *
 * <p>Current implementation provides contract-aligned capabilities:
 *
 * <ul>
 *   <li>{@code force-recalc}: trigger full rebuild for {@code specimen_match_pair}
 *   <li>{@code reset-elo}: reset Elo for configurable low-vote subset
 *   <li>{@code match-quality}: report pair coverage/profile consistency for operations
 * </ul>
 */
@Service
public class ArenaAdminService {

  private static final Logger log = LoggerFactory.getLogger(ArenaAdminService.class);

  private static final String FORCE_RECALC_REASON = "ADMIN_FORCE_RECALC";
  private static final String RESET_ELO_REASON = "ADMIN_RESET_ELO";
  private static final String UNKNOWN_PROFILE_VERSION = "unknown";
  private static final int RATIO_SCALE = 4;
  private static final int AVERAGE_SCALE = 2;

  private final ArenaSpecimenMatchPairRebuildService matchPairRebuildService;
  private final ArenaSpecimenMatchReadModel specimenMatchReadModel;
  private final ArenaSpecimenMatchPairReadModel specimenMatchPairReadModel;
  private final ArenaSpecimenRatingStore arenaSpecimenRatingStore;
  private final SpecimenRatingJpaRepository specimenRatingJpaRepository;
  private final ArenaRuntimePolicyPort arenaRuntimePolicyPort;
  private final ArenaMatchProperties arenaMatchProperties;
  private final ArenaMatchProfilePort arenaMatchProfilePort;

  public ArenaAdminService(
      ArenaSpecimenMatchPairRebuildService matchPairRebuildService,
      ArenaSpecimenMatchReadModel specimenMatchReadModel,
      ArenaSpecimenMatchPairReadModel specimenMatchPairReadModel,
      ArenaSpecimenRatingStore arenaSpecimenRatingStore,
      SpecimenRatingJpaRepository specimenRatingJpaRepository,
      ArenaRuntimePolicyPort arenaRuntimePolicyPort,
      ArenaMatchProperties arenaMatchProperties,
      ArenaMatchProfilePort arenaMatchProfilePort) {
    this.matchPairRebuildService = matchPairRebuildService;
    this.specimenMatchReadModel = specimenMatchReadModel;
    this.specimenMatchPairReadModel = specimenMatchPairReadModel;
    this.arenaSpecimenRatingStore = arenaSpecimenRatingStore;
    this.specimenRatingJpaRepository = specimenRatingJpaRepository;
    this.arenaRuntimePolicyPort = arenaRuntimePolicyPort;
    this.arenaMatchProperties = arenaMatchProperties;
    this.arenaMatchProfilePort = arenaMatchProfilePort;
  }

  /**
   * Triggers deterministic full rebuild for precomputed pair table.
   *
   * <p>Current implementation focuses on pair source recalculation because duel matching is
   * pair-first.
   */
  @Transactional
  public ForceRecalcResult forceRecalc(String adminUserId, String reason) {
    String rebuildReason = buildOperationReason(FORCE_RECALC_REASON, adminUserId, reason);
    ArenaSpecimenMatchPairRebuildService.PairRebuildResult rebuildResult =
        matchPairRebuildService.rebuildAllPairs(rebuildReason);
    MatchQualityReport qualityReport = evaluateMatchQuality();

    return new ForceRecalcResult(
        rebuildReason,
        rebuildResult.activeSpecimens(),
        rebuildResult.deletedPairs(),
        rebuildResult.upsertedPairs(),
        qualityReport);
  }

  /**
   * Resets Elo for active specimens while preserving high-vote rows.
   *
   * <p>Rows with {@code matchesPlayed >= resetExcludeThreshold} are intentionally skipped to avoid
   * rewriting mature ratings during emergency maintenance. A threshold of {@code <= 0} disables
   * exclusion and resets all locked active rows.
   */
  @Transactional
  public ResetEloResult resetElo(String adminUserId, String reason) {
    String resetReason = buildOperationReason(RESET_ELO_REASON, adminUserId, reason);
    int targetElo = arenaRuntimePolicyPort.currentArenaRuntimePolicy().initialElo();
    int resetExcludeThreshold = normalizeResetExcludeThreshold(arenaMatchProperties.getResetExcludeThreshold());

    Set<String> activeSpecimenIds = resolveActiveSpecimenIds();
    if (activeSpecimenIds.isEmpty()) {
      MatchQualityReport qualityReport = evaluateMatchQuality();
      return new ResetEloResult(
          resetReason,
          targetElo,
          resetExcludeThreshold,
          0,
          0,
          0,
          0,
          0,
          0,
          qualityReport);
    }

    List<SpecimenRatingJpaEntity> lockedRatings = lockActiveRatings(activeSpecimenIds);
    int missingSpecimens = Math.max(0, activeSpecimenIds.size() - lockedRatings.size());

    int excludedSpecimens = 0;
    int resetCandidates = 0;
    int updatedSpecimens = 0;
    for (SpecimenRatingJpaEntity rating : lockedRatings) {
      if (shouldExcludeFromReset(rating.getMatchesPlayed(), resetExcludeThreshold)) {
        excludedSpecimens += 1;
        continue;
      }
      resetCandidates += 1;
      if (rating.resetEloToBaseline(targetElo)) {
        updatedSpecimens += 1;
      }
    }

    specimenRatingJpaRepository.saveAll(lockedRatings);
    MatchQualityReport qualityReport = evaluateMatchQuality();

    log.info(
        "arena_admin_reset_elo reason={} targetElo={} activeSpecimens={} lockedSpecimens={} resetCandidates={} updatedSpecimens={} excludedSpecimens={} missingSpecimens={}",
        resetReason,
        targetElo,
        activeSpecimenIds.size(),
        lockedRatings.size(),
        resetCandidates,
        updatedSpecimens,
        excludedSpecimens,
        missingSpecimens);

    return new ResetEloResult(
        resetReason,
        targetElo,
        resetExcludeThreshold,
        activeSpecimenIds.size(),
        lockedRatings.size(),
        resetCandidates,
        updatedSpecimens,
        excludedSpecimens,
        missingSpecimens,
        qualityReport);
  }

  /**
   * Builds runtime quality report for current precomputed pair table.
   *
   * <p>This report helps operations validate whether Arena duel inputs are complete and profile
   * version is consistent with published M04 match profile config.
   */
  @Transactional(readOnly = true)
  public MatchQualityReport evaluateMatchQuality() {
    Set<String> activeSpecimenIds = resolveActiveSpecimenIds();
    int activeSpecimenCount = activeSpecimenIds.size();
    int expectedPairCount = resolveExpectedPairCount(activeSpecimenCount);

    List<ArenaSpecimenMatchPairReadModel.SpecimenMatchPair> pairs =
        specimenMatchPairReadModel.listActivePairs();
    int availablePairCount = pairs.size();

    EnumMap<ArenaMatchType, Integer> matchTypeCounters = initializeMatchTypeCounters();
    Map<String, Integer> profileVersionCounters = new HashMap<>();

    boolean hasScore = false;
    int minScore = 0;
    int maxScore = 0;
    long scoreSum = 0L;
    for (ArenaSpecimenMatchPairReadModel.SpecimenMatchPair pair : pairs) {
      if (pair == null) {
        continue;
      }

      ArenaMatchType matchType = pair.matchType() == null ? ArenaMatchType.CROSS : pair.matchType();
      matchTypeCounters.compute(matchType, (ignored, count) -> count == null ? 1 : count + 1);

      String profileVersion = normalizeProfileVersion(pair.matchProfileVersion());
      profileVersionCounters.merge(profileVersion, 1, Integer::sum);

      int matchScore = pair.matchScore();
      if (!hasScore) {
        minScore = matchScore;
        maxScore = matchScore;
        hasScore = true;
      } else {
        minScore = Math.min(minScore, matchScore);
        maxScore = Math.max(maxScore, matchScore);
      }
      scoreSum += matchScore;
    }

    String configuredProfileVersion =
        normalizeProfileVersion(arenaMatchProfilePort.currentProfile().profileVersion());
    boolean profileVersionAligned =
        resolveProfileVersionAligned(
            configuredProfileVersion,
            profileVersionCounters,
            expectedPairCount,
            availablePairCount);

    return new MatchQualityReport(
        activeSpecimenCount,
        expectedPairCount,
        availablePairCount,
        expectedPairCount == availablePairCount,
        resolveCoverageRatio(expectedPairCount, availablePairCount),
        arenaMatchProperties.getResetExcludeThreshold(),
        configuredProfileVersion,
        profileVersionAligned,
        toMatchTypeBreakdown(matchTypeCounters),
        toProfileVersionBreakdown(profileVersionCounters),
        toScoreSummary(hasScore, minScore, maxScore, scoreSum, availablePairCount));
  }

  private String buildOperationReason(String operation, String adminUserId, String reason) {
    StringBuilder reasonBuilder = new StringBuilder(operation);
    if (StringUtils.hasText(adminUserId)) {
      reasonBuilder.append(':').append(adminUserId.trim());
    }
    if (StringUtils.hasText(reason)) {
      reasonBuilder.append(':').append(reason.trim());
    }
    return reasonBuilder.toString();
  }

  private Set<String> resolveActiveSpecimenIds() {
    Set<String> specimenIds = new TreeSet<>();
    for (ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate :
        specimenMatchReadModel.listActiveCandidates()) {
      if (candidate == null || !StringUtils.hasText(candidate.specimenId())) {
        continue;
      }
      specimenIds.add(candidate.specimenId().trim());
    }
    return specimenIds;
  }

  private List<SpecimenRatingJpaEntity> lockActiveRatings(Set<String> activeSpecimenIds) {
    List<SpecimenRatingJpaEntity> ratings =
        specimenRatingJpaRepository.findAllBySpecimenIdInForUpdate(activeSpecimenIds);

    Set<String> missingSpecimenIds = new HashSet<>(activeSpecimenIds);
    ratings.forEach(rating -> missingSpecimenIds.remove(rating.getSpecimenId()));
    for (String missingSpecimenId : missingSpecimenIds) {
      // Bootstrap migration path from legacy metrics if this is an old specimen row.
      arenaSpecimenRatingStore.findForUpdate(missingSpecimenId);
    }

    return specimenRatingJpaRepository.findAllBySpecimenIdInForUpdate(activeSpecimenIds);
  }

  private int normalizeResetExcludeThreshold(int threshold) {
    return Math.max(0, threshold);
  }

  private boolean shouldExcludeFromReset(int matchesPlayed, int resetExcludeThreshold) {
    return resetExcludeThreshold > 0 && matchesPlayed >= resetExcludeThreshold;
  }

  private int resolveExpectedPairCount(int activeSpecimenCount) {
    if (activeSpecimenCount < 2) {
      return 0;
    }
    long expectedPairs = ((long) activeSpecimenCount * (activeSpecimenCount - 1L)) / 2L;
    return expectedPairs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) expectedPairs;
  }

  private EnumMap<ArenaMatchType, Integer> initializeMatchTypeCounters() {
    EnumMap<ArenaMatchType, Integer> counters = new EnumMap<>(ArenaMatchType.class);
    for (ArenaMatchType matchType : ArenaMatchType.values()) {
      counters.put(matchType, 0);
    }
    return counters;
  }

  private String normalizeProfileVersion(String profileVersion) {
    if (!StringUtils.hasText(profileVersion)) {
      return UNKNOWN_PROFILE_VERSION;
    }
    return profileVersion.trim();
  }

  private boolean resolveProfileVersionAligned(
      String configuredProfileVersion,
      Map<String, Integer> profileVersionCounters,
      int expectedPairCount,
      int availablePairCount) {
    if (expectedPairCount == 0 && availablePairCount == 0) {
      return true;
    }
    if (profileVersionCounters.size() != 1) {
      return false;
    }
    return profileVersionCounters.containsKey(configuredProfileVersion);
  }

  private BigDecimal resolveCoverageRatio(int expectedPairCount, int availablePairCount) {
    if (expectedPairCount <= 0) {
      return availablePairCount == 0
          ? BigDecimal.ONE.setScale(RATIO_SCALE, RoundingMode.HALF_UP)
          : BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf(availablePairCount)
        .divide(BigDecimal.valueOf(expectedPairCount), RATIO_SCALE, RoundingMode.HALF_UP);
  }

  private List<MatchTypeBreakdown> toMatchTypeBreakdown(EnumMap<ArenaMatchType, Integer> counters) {
    List<MatchTypeBreakdown> breakdown = new ArrayList<>(ArenaMatchType.values().length);
    for (ArenaMatchType matchType : ArenaMatchType.values()) {
      breakdown.add(new MatchTypeBreakdown(matchType, counters.getOrDefault(matchType, 0)));
    }
    return List.copyOf(breakdown);
  }

  private List<ProfileVersionBreakdown> toProfileVersionBreakdown(
      Map<String, Integer> profileVersionCounters) {
    return profileVersionCounters.entrySet().stream()
        .sorted(
            Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                .reversed()
                .thenComparing(Map.Entry::getKey))
        .map(entry -> new ProfileVersionBreakdown(entry.getKey(), entry.getValue()))
        .toList();
  }

  private ScoreSummary toScoreSummary(
      boolean hasScore, int minScore, int maxScore, long scoreSum, int availablePairCount) {
    if (!hasScore || availablePairCount <= 0) {
      return new ScoreSummary(0, 0, BigDecimal.ZERO.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP));
    }
    BigDecimal average =
        BigDecimal.valueOf(scoreSum)
            .divide(BigDecimal.valueOf(availablePairCount), AVERAGE_SCALE, RoundingMode.HALF_UP);
    return new ScoreSummary(minScore, maxScore, average);
  }

  /** Force-recalc execution summary. */
  public record ForceRecalcResult(
      String rebuildReason,
      int activeSpecimens,
      int deletedPairs,
      int upsertedPairs,
      MatchQualityReport matchQuality) {}

  /** Reset-elo execution summary. */
  public record ResetEloResult(
      String resetReason,
      int targetElo,
      int resetExcludeThreshold,
      int activeSpecimens,
      int lockedSpecimens,
      int resetCandidates,
      int updatedSpecimens,
      int excludedSpecimens,
      int missingSpecimens,
      MatchQualityReport matchQuality) {}

  /** Match-quality report used by Arena admin diagnostics. */
  public record MatchQualityReport(
      int activeSpecimenCount,
      int expectedPairCount,
      int availablePairCount,
      boolean pairCoverageComplete,
      BigDecimal pairCoverageRatio,
      int resetExcludeThreshold,
      String configuredProfileVersion,
      boolean profileVersionAligned,
      List<MatchTypeBreakdown> matchTypeBreakdown,
      List<ProfileVersionBreakdown> profileVersionBreakdown,
      ScoreSummary scoreSummary) {}

  /** Pair count by {@link ArenaMatchType}. */
  public record MatchTypeBreakdown(ArenaMatchType matchType, int pairCount) {}

  /** Pair count by {@code matchProfileVersion}. */
  public record ProfileVersionBreakdown(String profileVersion, int pairCount) {}

  /** Basic score statistics for current pair table. */
  public record ScoreSummary(int minScore, int maxScore, BigDecimal averageScore) {}
}
