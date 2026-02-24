package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogQuery;

/** Persistence port for admin audit logs. */
public interface AdminAuditLogStore {

  void append(AdminAuditLogEntry entry);

  AdminAuditLogPage list(AdminAuditLogQuery query);
}
