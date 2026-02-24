package com.wtfrepo.backend.admin.infra.persistence.adapter;

import com.wtfrepo.backend.admin.application.AdminBootstrapStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminBootstrapRecord;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminBootstrapRecordJpaEntity;
import com.wtfrepo.backend.admin.infra.persistence.repository.AdminBootstrapRecordJpaRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnBean(AdminBootstrapRecordJpaRepository.class)
public class JpaAdminBootstrapStore implements AdminBootstrapStore {

  private final AdminBootstrapRecordJpaRepository repository;

  public JpaAdminBootstrapStore(AdminBootstrapRecordJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminBootstrapRecord> latest() {
    return repository.findTopByOrderByBootstrappedAtDesc().map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists() {
    return repository.count() > 0;
  }

  @Override
  @Transactional
  public AdminBootstrapRecord save(AdminBootstrapRecord record) {
    AdminBootstrapRecordJpaEntity entity =
        AdminBootstrapRecordJpaEntity.create(
            record.adminUserId(),
            record.emailUsed(),
            record.ipAddress(),
            record.userAgent(),
            record.bootstrappedAt());
    AdminBootstrapRecordJpaEntity saved = repository.save(entity);
    return toRecord(saved);
  }

  private AdminBootstrapRecord toRecord(AdminBootstrapRecordJpaEntity entity) {
    return new AdminBootstrapRecord(
        entity.getAdminUserId(),
        entity.getEmailUsed(),
        entity.getBootstrappedAt(),
        entity.getIpAddress(),
        entity.getUserAgent());
  }
}
