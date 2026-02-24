package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminRoleRecord;
import com.wtfrepo.backend.admin.domain.AdminRole;
import java.util.List;
import java.util.Optional;

/** Persistence port for admin role assignments. */
public interface AdminRoleStore {

  List<AdminRoleRecord> findActiveRoles(String userId);

  Optional<AdminRoleRecord> findActiveRole(String userId, AdminRole role);

  boolean existsActiveRole(AdminRole role);

  List<AdminRoleRecord> findActiveByRole(AdminRole role);

  AdminRoleRecord grantRole(String userId, AdminRole role, String grantedBy);

  Optional<AdminRoleRecord> revokeRole(String userId, AdminRole role, String revokedBy);
}
