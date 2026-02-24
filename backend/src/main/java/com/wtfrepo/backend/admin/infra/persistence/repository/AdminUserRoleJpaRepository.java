package com.wtfrepo.backend.admin.infra.persistence.repository;

import com.wtfrepo.backend.admin.domain.AdminRole;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminUserRoleJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRoleJpaRepository extends JpaRepository<AdminUserRoleJpaEntity, String> {

  Optional<AdminUserRoleJpaEntity> findByUserIdAndRole(String userId, AdminRole role);

  Optional<AdminUserRoleJpaEntity> findByUserIdAndRoleAndActiveTrue(String userId, AdminRole role);

  List<AdminUserRoleJpaEntity> findByUserIdAndActiveTrue(String userId);

  List<AdminUserRoleJpaEntity> findByRoleAndActiveTrue(AdminRole role);

  boolean existsByRoleAndActiveTrue(AdminRole role);
}
