package com.wtfrepo.backend.arena.infra.persistence.adapter;

import com.wtfrepo.backend.arena.application.ArenaSpecimenRating;
import com.wtfrepo.backend.arena.application.ArenaSpecimenRatingStore;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arena rating store backed by {@code specimen_rating}.
 */
@Component
@Primary
@ConditionalOnBean(SpecimenRatingJpaRepository.class)
public class JpaArenaSpecimenRatingStore implements ArenaSpecimenRatingStore {

  private final SpecimenRatingJpaRepository ratingRepository;
  private final Optional<SpecimenArenaMetricsJpaRepository> legacyMetricsRepository;

  public JpaArenaSpecimenRatingStore(
      SpecimenRatingJpaRepository ratingRepository,
      ObjectProvider<SpecimenArenaMetricsJpaRepository> legacyMetricsRepository) {
    this.ratingRepository = ratingRepository;
    this.legacyMetricsRepository = Optional.ofNullable(legacyMetricsRepository.getIfAvailable());
  }

  @Override
  @Transactional
  public Optional<ArenaSpecimenRating> find(String specimenId) {
    return findOrBootstrap(specimenId, false).map(this::toRating);
  }

  @Override
  @Transactional
  public Optional<ArenaSpecimenRating> findForUpdate(String specimenId) {
    return findOrBootstrap(specimenId, true).map(this::toRating);
  }

  @Override
  @Transactional
  public ArenaSpecimenRating applyVoteDelta(String specimenId, int eloDelta) {
    SpecimenRatingJpaEntity entity =
        findOrBootstrap(specimenId, true)
            .orElseThrow(() -> new IllegalStateException("Missing rating for specimenId=" + specimenId));
    entity.applyVoteDelta(eloDelta);
    ratingRepository.save(entity);
    return toRating(entity);
  }

  private Optional<SpecimenRatingJpaEntity> findOrBootstrap(String specimenId, boolean forUpdate) {
    Optional<SpecimenRatingJpaEntity> existing =
        forUpdate
            ? ratingRepository.findBySpecimenIdForUpdate(specimenId)
            : ratingRepository.findById(specimenId);
    if (existing.isPresent()) {
      return existing;
    }

    Optional<SpecimenRatingJpaEntity> bootstrapCandidate = bootstrapFromLegacyMetrics(specimenId);
    if (bootstrapCandidate.isEmpty()) {
      return Optional.empty();
    }

    try {
      ratingRepository.saveAndFlush(bootstrapCandidate.get());
    } catch (DataIntegrityViolationException ex) {
      // Concurrent request bootstrapped the same row first; reload below.
    }

    return forUpdate
        ? ratingRepository.findBySpecimenIdForUpdate(specimenId)
        : ratingRepository.findById(specimenId);
  }

  private Optional<SpecimenRatingJpaEntity> bootstrapFromLegacyMetrics(String specimenId) {
    Optional<SpecimenArenaMetricsJpaRepository> legacyRepository = legacyMetricsRepository;
    if (legacyRepository.isEmpty()) {
      return Optional.empty();
    }

    return legacyRepository
        .get()
        .findById(specimenId)
        .map(this::toRatingEntityFromLegacyMetrics);
  }

  private SpecimenRatingJpaEntity toRatingEntityFromLegacyMetrics(
      SpecimenArenaMetricsJpaEntity legacyMetrics) {
    return SpecimenRatingJpaEntity.createFromLegacyMetrics(
        legacyMetrics.getSpecimenId(), legacyMetrics.getElo(), legacyMetrics.getVotes());
  }

  private ArenaSpecimenRating toRating(SpecimenRatingJpaEntity entity) {
    return new ArenaSpecimenRating(
        entity.getSpecimenId(),
        entity.getEloScore(),
        entity.getMatchesPlayed(),
        entity.getRecentAppearances());
  }
}
