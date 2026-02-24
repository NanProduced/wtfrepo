package com.wtfrepo.backend.admin.infra.persistence.repository;

import com.wtfrepo.backend.admin.infra.persistence.entity.AdminBootstrapRecordJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminBootstrapRecordJpaRepository
    extends JpaRepository<AdminBootstrapRecordJpaEntity, String> {

  Optional<AdminBootstrapRecordJpaEntity> findTopByOrderByBootstrappedAtDesc();
}
