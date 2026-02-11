package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenGithubMetadataJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenGithubMetadataJpaRepository
    extends JpaRepository<SpecimenGithubMetadataJpaEntity, String> {}
