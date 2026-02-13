package com.wtfrepo.backend.arena.infra.persistence.repository;

import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenRatingJpaRepository
    extends JpaRepository<SpecimenRatingJpaEntity, String> {}

