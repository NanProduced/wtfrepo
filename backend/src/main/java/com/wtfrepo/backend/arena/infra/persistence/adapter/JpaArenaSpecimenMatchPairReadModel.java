package com.wtfrepo.backend.arena.infra.persistence.adapter;

import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchPairReadModel;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenMatchPairJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenMatchPairJpaRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed read model for precomputed duel pairs.
 */
@Component
@ConditionalOnBean(SpecimenMatchPairJpaRepository.class)
public class JpaArenaSpecimenMatchPairReadModel implements ArenaSpecimenMatchPairReadModel {

  private final SpecimenMatchPairJpaRepository specimenMatchPairJpaRepository;

  public JpaArenaSpecimenMatchPairReadModel(
      SpecimenMatchPairJpaRepository specimenMatchPairJpaRepository) {
    this.specimenMatchPairJpaRepository = specimenMatchPairJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SpecimenMatchPair> listActivePairs() {
    return specimenMatchPairJpaRepository
        .findAllByOrderByMatchScoreDescLeftSpecimenIdAscRightSpecimenIdAsc()
        .stream()
        .map(this::toReadModel)
        .toList();
  }

  private SpecimenMatchPair toReadModel(SpecimenMatchPairJpaEntity entity) {
    return new SpecimenMatchPair(
        entity.getLeftSpecimenId(),
        entity.getRightSpecimenId(),
        entity.getMatchType(),
        entity.getMatchScore(),
        entity.getMatchProfileVersion());
  }
}
