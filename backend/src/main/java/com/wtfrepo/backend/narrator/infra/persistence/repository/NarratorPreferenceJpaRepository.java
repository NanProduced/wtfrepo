package com.wtfrepo.backend.narrator.infra.persistence.repository;

import com.wtfrepo.backend.narrator.infra.persistence.entity.NarratorPreferenceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NarratorPreferenceJpaRepository
    extends JpaRepository<NarratorPreferenceJpaEntity, String> {}
