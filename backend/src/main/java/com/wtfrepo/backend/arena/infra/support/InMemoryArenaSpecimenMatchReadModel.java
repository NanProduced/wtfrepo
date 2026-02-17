package com.wtfrepo.backend.arena.infra.support;

import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchReadModel;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Minimal in-memory match read model fallback for contexts without JPA repositories.
 *
 * <p>Used by lightweight tests that do not wire DataSource/JPA infrastructure.
 */
@Component
@ConditionalOnMissingBean(SpecimenJpaRepository.class)
public class InMemoryArenaSpecimenMatchReadModel implements ArenaSpecimenMatchReadModel {

  @Override
  public List<SpecimenMatchCandidate> listActiveCandidates() {
    return List.of();
  }
}
