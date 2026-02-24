package com.wtfrepo.backend.admin.infra.persistence.adapter;

import com.wtfrepo.backend.admin.application.AdminRoleStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminRoleRecord;
import com.wtfrepo.backend.admin.domain.AdminRole;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminUserRoleJpaEntity;
import com.wtfrepo.backend.admin.infra.persistence.repository.AdminUserRoleJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnBean(AdminUserRoleJpaRepository.class)
public class JpaAdminRoleStore implements AdminRoleStore {

  private final AdminUserRoleJpaRepository repository;

  public JpaAdminRoleStore(AdminUserRoleJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminRoleRecord> findActiveRoles(String userId) {
    return repository.findByUserIdAndActiveTrue(userId).stream().map(this::toRecord).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminRoleRecord> findActiveRole(String userId, AdminRole role) {
    return repository.findByUserIdAndRoleAndActiveTrue(userId, role).map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsActiveRole(AdminRole role) {
    return repository.existsByRoleAndActiveTrue(role);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminRoleRecord> findActiveByRole(AdminRole role) {
    return repository.findByRoleAndActiveTrue(role).stream().map(this::toRecord).toList();
  }

  @Override
  @Transactional
  public AdminRoleRecord grantRole(String userId, AdminRole role, String grantedBy) {
    Optional<AdminUserRoleJpaEntity> existing = repository.findByUserIdAndRole(userId, role);
    if (existing.isPresent()) {
      AdminUserRoleJpaEntity entity = existing.get();
      if (entity.isActive()) {
        return toRecord(entity);
      }
      entity.activate(grantedBy);
      AdminUserRoleJpaEntity saved = repository.save(entity);
      return toRecord(saved);
    }

    AdminUserRoleJpaEntity entity = AdminUserRoleJpaEntity.create(userId, role, grantedBy);
    AdminUserRoleJpaEntity saved = repository.save(entity);
    return toRecord(saved);
  }

  @Override
  @Transactional
  public Optional<AdminRoleRecord> revokeRole(String userId, AdminRole role, String revokedBy) {
    Optional<AdminUserRoleJpaEntity> existing =
        repository.findByUserIdAndRoleAndActiveTrue(userId, role);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    AdminUserRoleJpaEntity entity = existing.get();
    entity.revoke();
    AdminUserRoleJpaEntity saved = repository.save(entity);
    AdminRoleRecord record =
        new AdminRoleRecord(
            saved.getUserId(),
            saved.getRole(),
            saved.isActive(),
            saved.getGrantedBy(),
            saved.getGrantedAt(),
            revokedBy,
            saved.getRevokedAt());
    return Optional.of(record);
  }

  private AdminRoleRecord toRecord(AdminUserRoleJpaEntity entity) {
    return new AdminRoleRecord(
        entity.getUserId(),
        entity.getRole(),
        entity.isActive(),
        entity.getGrantedBy(),
        entity.getGrantedAt(),
        null,
        entity.getRevokedAt());
  }
}
