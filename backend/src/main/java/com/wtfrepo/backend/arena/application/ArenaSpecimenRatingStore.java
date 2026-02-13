package com.wtfrepo.backend.arena.application;

import java.util.Optional;

/**
 * Store abstraction for arena-readable specimen rating state.
 *
 * <p>TODO(M01-arena): switch backing store from {@code specimen_arena_metrics} to
 * {@code specimen_rating} once schema and module ownership is finalized.
 */
public interface ArenaSpecimenRatingStore {

  Optional<ArenaSpecimenRating> find(String specimenId);

  /**
   * Returns rating with write lock semantics when backing store supports it.
   *
   * <p>Default implementation falls back to plain read, which keeps test doubles and non-JPA
   * adapters minimal while allowing JPA adapter to enforce pessimistic locking.
   */
  default Optional<ArenaSpecimenRating> findForUpdate(String specimenId) {
    return find(specimenId);
  }

  ArenaSpecimenRating applyVoteDelta(String specimenId, int eloDelta);
}
