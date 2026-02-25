package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenCommunityMetricsJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for specimen community metrics. */
public interface SpecimenCommunityMetricsJpaRepository
    extends JpaRepository<SpecimenCommunityMetricsJpaEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select metrics
      from SpecimenCommunityMetricsJpaEntity metrics
      where metrics.specimenId = :specimenId
      """)
  Optional<SpecimenCommunityMetricsJpaEntity> findBySpecimenIdForUpdate(
      @Param("specimenId") String specimenId);
}
