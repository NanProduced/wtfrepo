package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanRecord;
import java.util.Optional;

/** Persistence port for admin user ban records. */
public interface AdminUserBanStore {

  Optional<AdminUserBanRecord> findActiveByUserId(String userId);

  AdminUserBanPage list(AdminUserBanQuery query);

  AdminUserBanRecord save(AdminUserBanRecord record);
}
