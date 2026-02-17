package com.wtfrepo.backend.arena.infra.support;

import com.wtfrepo.backend.arena.application.ArenaSpecimenRating;
import com.wtfrepo.backend.arena.application.ArenaSpecimenRatingStore;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-memory specimen rating store kept for unit tests.
 *
 * <p>Production path should use JPA-backed rating store.
 */
@Component
@ConditionalOnMissingBean(SpecimenRatingJpaRepository.class)
public class InMemoryArenaSpecimenRatingStore implements ArenaSpecimenRatingStore {

  private final Map<String, ArenaSpecimenRating> store = new ConcurrentHashMap<>();

  @Override
  public Optional<ArenaSpecimenRating> find(String specimenId) {
    return Optional.ofNullable(store.get(specimenId));
  }

  @Override
  public ArenaSpecimenRating applyVoteDelta(String specimenId, int eloDelta) {
    ArenaSpecimenRating rating = store.get(specimenId);
    if (rating == null) {
      throw new IllegalStateException("Missing rating for specimenId=" + specimenId);
    }

    ArenaSpecimenRating updated =
        new ArenaSpecimenRating(
            specimenId,
            rating.eloScore() + eloDelta,
            rating.matchesPlayed() + 1,
            rating.recentAppearances());
    store.put(specimenId, updated);
    return updated;
  }
}
