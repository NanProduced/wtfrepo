package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import java.util.List;

/**
 * Read-only projection for precomputed specimen match pairs.
 *
 * <p>Rows are maintained by {@link ArenaSpecimenMatchPairRebuildService} and consumed by duel
 * matching as the primary candidate source.
 */
public interface ArenaSpecimenMatchPairReadModel {

  List<SpecimenMatchPair> listActivePairs();

  record SpecimenMatchPair(
      String leftSpecimenId,
      String rightSpecimenId,
      ArenaMatchType matchType,
      int matchScore,
      String matchProfileVersion) {}
}
