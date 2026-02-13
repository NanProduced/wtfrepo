package com.wtfrepo.backend.arena.infra.persistence.adapter;

import com.wtfrepo.backend.arena.application.ArenaSpecimenRating;
import com.wtfrepo.backend.arena.application.ArenaSpecimenRatingStore;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arena rating store backed by {@code specimen_arena_metrics}.
 *
 * <p>TODO(M01-arena): replace with {@code specimen_rating} table once M01 schema is finalized.
 */
@Component
@Primary
@ConditionalOnBean(SpecimenArenaMetricsJpaRepository.class)
public class JpaArenaSpecimenRatingStore implements ArenaSpecimenRatingStore {

  private final SpecimenArenaMetricsJpaRepository repository;

  public JpaArenaSpecimenRatingStore(SpecimenArenaMetricsJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ArenaSpecimenRating> find(String specimenId) {
    return repository.findById(specimenId).map(this::toRating);
  }

  @Override
  @Transactional
  public Optional<ArenaSpecimenRating> findForUpdate(String specimenId) {
    return repository.findBySpecimenIdForUpdate(specimenId).map(this::toRating);
  }

  @Override
  @Transactional
  public ArenaSpecimenRating applyVoteDelta(String specimenId, int eloDelta) {
    SpecimenArenaMetricsJpaEntity entity =
        repository
            .findBySpecimenIdForUpdate(specimenId)
            .orElseThrow(() -> new IllegalStateException("Missing metrics for specimenId=" + specimenId));
    entity.applyArenaVoteDelta(eloDelta);
    repository.save(entity);
    return toRating(entity);
  }

  private ArenaSpecimenRating toRating(SpecimenArenaMetricsJpaEntity entity) {
    return new ArenaSpecimenRating(entity.getSpecimenId(), entity.getElo(), entity.getVotes());
  }
}
