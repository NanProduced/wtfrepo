package com.wtfrepo.backend.arena.application;

import java.util.List;

/**
 * M01 read-only projection for duel matching candidates.
 *
 * <p>This is the implementation seam for contract TODO(M01+M04): SpecimenMatchReadModel.
 */
public interface ArenaSpecimenMatchReadModel {

  List<SpecimenMatchCandidate> listActiveCandidates();

  record SpecimenMatchCandidate(
      String specimenId,
      String title,
      String tagline,
      String thumbnailUrl,
      String species,
      List<String> diagnosisTags) {}
}