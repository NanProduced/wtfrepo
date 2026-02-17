package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenMatchPairJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenMatchPairJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Rebuilds precomputed rows in {@code specimen_match_pair}.
 *
 * <p>Contract expects this table to be maintained by M04 lifecycle/config events. The service is
 * intentionally stateless and idempotent: it recomputes deterministic rows from current read model
 * snapshot instead of applying incremental arithmetic updates.
 */
@Service
public class ArenaSpecimenMatchPairRebuildService {

  private static final Logger log = LoggerFactory.getLogger(ArenaSpecimenMatchPairRebuildService.class);

  private final ArenaSpecimenMatchReadModel specimenMatchReadModel;
  private final SpecimenMatchPairJpaRepository specimenMatchPairJpaRepository;
  private final ArenaMatchProperties arenaMatchProperties;

  public ArenaSpecimenMatchPairRebuildService(
      ArenaSpecimenMatchReadModel specimenMatchReadModel,
      SpecimenMatchPairJpaRepository specimenMatchPairJpaRepository,
      ArenaMatchProperties arenaMatchProperties) {
    this.specimenMatchReadModel = specimenMatchReadModel;
    this.specimenMatchPairJpaRepository = specimenMatchPairJpaRepository;
    this.arenaMatchProperties = arenaMatchProperties;
  }

  /**
   * Full rebuild for all active specimens.
   *
   * <p>Used for heavyweight configuration changes such as MATCH_PROFILE/SPECIES_ADJACENCY update.
   */
  @Transactional
  public PairRebuildResult rebuildAllPairs(String reason) {
    Map<String, ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> candidatesById =
        loadActiveCandidatesBySpecimenId();
    List<ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> activeCandidates =
        new ArrayList<>(candidatesById.values());
    Set<String> adjacentSpeciesPairs =
        normalizeAdjacentPairs(arenaMatchProperties.getAdjacentSpeciesPairs());
    long existingPairs = specimenMatchPairJpaRepository.count();

    List<SpecimenMatchPairJpaEntity> recomputedPairs =
        buildAllPairs(activeCandidates, adjacentSpeciesPairs, Instant.now());
    specimenMatchPairJpaRepository.deleteAllInBatch();
    saveAll(recomputedPairs);

    PairRebuildResult result =
        new PairRebuildResult(activeCandidates.size(), clampToInt(existingPairs), recomputedPairs.size());
    log.info(
        "arena_match_pair_full_rebuild reason={} activeSpecimens={} deletedPairs={} upsertedPairs={}",
        reason,
        result.activeSpecimens(),
        result.deletedPairs(),
        result.upsertedPairs());
    return result;
  }

  /**
   * Incremental rebuild for one specimen.
   *
   * <p>Used for SpecimenActivated/TagsChanged events. Method first removes all pair rows involving
   * the specimen, then recomputes deterministic rows against current ACTIVE candidate set.
   */
  @Transactional
  public PairRebuildResult rebuildPairsForSpecimen(String specimenId, String reason) {
    String normalizedSpecimenId = normalizeSpecimenId(specimenId);
    int deletedPairs = specimenMatchPairJpaRepository.deleteAllBySpecimenId(normalizedSpecimenId);

    Map<String, ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> candidatesById =
        loadActiveCandidatesBySpecimenId();
    ArenaSpecimenMatchReadModel.SpecimenMatchCandidate centerCandidate =
        candidatesById.get(normalizedSpecimenId);
    if (centerCandidate == null) {
      PairRebuildResult result =
          new PairRebuildResult(candidatesById.size(), deletedPairs, 0);
      log.info(
          "arena_match_pair_incremental_rebuild_skip reason={} specimenId={} activeSpecimens={} deletedPairs={}",
          reason,
          normalizedSpecimenId,
          result.activeSpecimens(),
          result.deletedPairs());
      return result;
    }

    Set<String> adjacentSpeciesPairs =
        normalizeAdjacentPairs(arenaMatchProperties.getAdjacentSpeciesPairs());
    List<SpecimenMatchPairJpaEntity> recomputedPairs =
        buildPairsForSpecimen(
            centerCandidate, candidatesById.values(), adjacentSpeciesPairs, Instant.now());
    saveAll(recomputedPairs);

    PairRebuildResult result =
        new PairRebuildResult(candidatesById.size(), deletedPairs, recomputedPairs.size());
    log.info(
        "arena_match_pair_incremental_rebuild reason={} specimenId={} activeSpecimens={} deletedPairs={} upsertedPairs={}",
        reason,
        normalizedSpecimenId,
        result.activeSpecimens(),
        result.deletedPairs(),
        result.upsertedPairs());
    return result;
  }

  /** Removes all pair rows containing one specimen. */
  @Transactional
  public int removePairsForSpecimen(String specimenId, String reason) {
    String normalizedSpecimenId = normalizeSpecimenId(specimenId);
    int deleted = specimenMatchPairJpaRepository.deleteAllBySpecimenId(normalizedSpecimenId);
    log.info(
        "arena_match_pair_remove_for_specimen reason={} specimenId={} deletedPairs={}",
        reason,
        normalizedSpecimenId,
        deleted);
    return deleted;
  }

  private Map<String, ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> loadActiveCandidatesBySpecimenId() {
    List<ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> activeCandidates =
        specimenMatchReadModel.listActiveCandidates();
    Map<String, ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> candidatesById =
        new LinkedHashMap<>();
    for (ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate : activeCandidates) {
      if (candidate == null || !StringUtils.hasText(candidate.specimenId())) {
        continue;
      }
      candidatesById.putIfAbsent(candidate.specimenId().trim(), candidate);
    }
    return candidatesById;
  }

  private List<SpecimenMatchPairJpaEntity> buildAllPairs(
      List<ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> candidates,
      Set<String> adjacentSpeciesPairs,
      Instant computedAt) {
    List<ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> sortedCandidates =
        candidates.stream()
            .sorted(
                (left, right) ->
                    left.specimenId().trim().compareTo(right.specimenId().trim()))
            .toList();
    List<SpecimenMatchPairJpaEntity> pairs = new ArrayList<>();
    for (int leftIndex = 0; leftIndex < sortedCandidates.size() - 1; leftIndex++) {
      for (int rightIndex = leftIndex + 1; rightIndex < sortedCandidates.size(); rightIndex++) {
        pairs.add(
            buildPair(
                sortedCandidates.get(leftIndex),
                sortedCandidates.get(rightIndex),
                adjacentSpeciesPairs,
                computedAt));
      }
    }
    return pairs;
  }

  private List<SpecimenMatchPairJpaEntity> buildPairsForSpecimen(
      ArenaSpecimenMatchReadModel.SpecimenMatchCandidate centerCandidate,
      Collection<ArenaSpecimenMatchReadModel.SpecimenMatchCandidate> allCandidates,
      Set<String> adjacentSpeciesPairs,
      Instant computedAt) {
    List<SpecimenMatchPairJpaEntity> pairs = new ArrayList<>();
    for (ArenaSpecimenMatchReadModel.SpecimenMatchCandidate other : allCandidates) {
      if (other == null
          || !StringUtils.hasText(other.specimenId())
          || centerCandidate.specimenId().equals(other.specimenId())) {
        continue;
      }
      pairs.add(buildPair(centerCandidate, other, adjacentSpeciesPairs, computedAt));
    }
    return pairs;
  }

  private SpecimenMatchPairJpaEntity buildPair(
      ArenaSpecimenMatchReadModel.SpecimenMatchCandidate left,
      ArenaSpecimenMatchReadModel.SpecimenMatchCandidate right,
      Set<String> adjacentSpeciesPairs,
      Instant computedAt) {
    ArenaMatchType matchType = resolveMatchType(left.species(), right.species(), adjacentSpeciesPairs);
    int matchScore = speciesScore(matchType) + diagnosisBonus(left.diagnosisTags(), right.diagnosisTags());
    return SpecimenMatchPairJpaEntity.create(
        left.specimenId(),
        right.specimenId(),
        matchType,
        matchScore,
        resolveProfileVersion(),
        computedAt);
  }

  private ArenaMatchType resolveMatchType(
      String leftSpecies, String rightSpecies, Set<String> adjacentSpeciesPairs) {
    if (!StringUtils.hasText(leftSpecies) || !StringUtils.hasText(rightSpecies)) {
      return ArenaMatchType.CROSS;
    }

    if (leftSpecies.equalsIgnoreCase(rightSpecies)) {
      return ArenaMatchType.SAME_SPECIES;
    }

    if (adjacentSpeciesPairs.contains(canonicalPair(leftSpecies, rightSpecies, ":"))) {
      return ArenaMatchType.ADJACENT;
    }
    return ArenaMatchType.CROSS;
  }

  private int speciesScore(ArenaMatchType matchType) {
    return switch (matchType) {
      case SAME_SPECIES -> arenaMatchProperties.getSameSpeciesScore();
      case ADJACENT -> arenaMatchProperties.getAdjacentSpeciesScore();
      case CROSS -> arenaMatchProperties.getCrossSpeciesScore();
    };
  }

  private int diagnosisBonus(List<String> leftDiagnosisTags, List<String> rightDiagnosisTags) {
    if (leftDiagnosisTags == null
        || rightDiagnosisTags == null
        || leftDiagnosisTags.isEmpty()
        || rightDiagnosisTags.isEmpty()) {
      return 0;
    }

    Set<String> left = normalizeDiagnosisTags(leftDiagnosisTags);
    left.retainAll(normalizeDiagnosisTags(rightDiagnosisTags));
    int bonus = left.size() * arenaMatchProperties.getDiagnosisBaseScore();
    return Math.min(arenaMatchProperties.getDiagnosisBonusCap(), bonus);
  }

  private Set<String> normalizeDiagnosisTags(List<String> diagnosisTags) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String diagnosisTag : diagnosisTags) {
      if (!StringUtils.hasText(diagnosisTag)) {
        continue;
      }
      normalized.add(diagnosisTag.trim());
    }
    return normalized;
  }

  private Set<String> normalizeAdjacentPairs(List<String> rawPairs) {
    if (rawPairs == null || rawPairs.isEmpty()) {
      return Set.of();
    }

    Set<String> normalized = new HashSet<>();
    for (String raw : rawPairs) {
      if (!StringUtils.hasText(raw) || !raw.contains(":")) {
        continue;
      }
      String[] segments = raw.split(":");
      if (segments.length != 2) {
        continue;
      }
      String left = segments[0].trim();
      String right = segments[1].trim();
      if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
        continue;
      }
      normalized.add(canonicalPair(left, right, ":"));
    }
    return normalized;
  }

  private String canonicalPair(String left, String right, String separator) {
    return left.compareTo(right) <= 0 ? left + separator + right : right + separator + left;
  }

  private String resolveProfileVersion() {
    if (!StringUtils.hasText(arenaMatchProperties.getProfileVersion())) {
      return "unknown";
    }
    return arenaMatchProperties.getProfileVersion().trim();
  }

  private void saveAll(List<SpecimenMatchPairJpaEntity> entities) {
    if (entities.isEmpty()) {
      return;
    }
    specimenMatchPairJpaRepository.saveAll(entities);
  }

  private String normalizeSpecimenId(String specimenId) {
    if (!StringUtils.hasText(specimenId)) {
      throw new IllegalArgumentException("specimenId must not be blank");
    }
    return specimenId.trim();
  }

  private int clampToInt(long value) {
    if (value < 0) {
      return 0;
    }
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  /** Rebuild summary for logs and tests. */
  public record PairRebuildResult(int activeSpecimens, int deletedPairs, int upsertedPairs) {}
}
