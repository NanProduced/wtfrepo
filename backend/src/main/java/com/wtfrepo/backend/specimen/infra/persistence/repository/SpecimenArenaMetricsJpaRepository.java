package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for specimen arena metrics. */
// TODO(M01-arena): move repository ownership with SpecimenArenaMetricsJpaEntity if metrics model
// is migrated to arena module.
public interface SpecimenArenaMetricsJpaRepository
    extends JpaRepository<SpecimenArenaMetricsJpaEntity, String> {

  /**
   * Pessimistic write lock avoids lost updates for concurrent votes touching same specimen row.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select metrics
      from SpecimenArenaMetricsJpaEntity metrics
      where metrics.specimenId = :specimenId
      """)
  Optional<SpecimenArenaMetricsJpaEntity> findBySpecimenIdForUpdate(
      @Param("specimenId") String specimenId);
}
