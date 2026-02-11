package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for specimen arena metrics. */
// TODO(M01-arena): move repository ownership with SpecimenArenaMetricsJpaEntity if metrics model
// is migrated to arena module.
public interface SpecimenArenaMetricsJpaRepository
    extends JpaRepository<SpecimenArenaMetricsJpaEntity, String> {}
