package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenAdminActionIdempotencyJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenAdminActionIdempotencyJpaRepository
    extends JpaRepository<SpecimenAdminActionIdempotencyJpaEntity, String> {

  void deleteByCreatedAtBefore(Instant threshold);
}
