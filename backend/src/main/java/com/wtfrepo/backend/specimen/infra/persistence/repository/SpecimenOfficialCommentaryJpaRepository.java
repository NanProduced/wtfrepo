package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenOfficialCommentaryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenOfficialCommentaryJpaRepository
    extends JpaRepository<SpecimenOfficialCommentaryJpaEntity, String> {}
