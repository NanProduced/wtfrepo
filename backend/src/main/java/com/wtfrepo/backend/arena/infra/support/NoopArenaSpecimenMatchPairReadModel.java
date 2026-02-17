package com.wtfrepo.backend.arena.infra.support;

import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchPairReadModel;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenMatchPairJpaRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Empty fallback when JPA pair table access is not available.
 *
 * <p>Duel service can still operate through runtime pair composition when fallback is enabled.
 */
@Component
@ConditionalOnMissingBean(SpecimenMatchPairJpaRepository.class)
public class NoopArenaSpecimenMatchPairReadModel implements ArenaSpecimenMatchPairReadModel {

  @Override
  public List<SpecimenMatchPair> listActivePairs() {
    return List.of();
  }
}
