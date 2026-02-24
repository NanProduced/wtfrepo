package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertRecord;
import java.util.Optional;

/** Persistence port for admin alert tasks. */
public interface AdminAlertStore {

  Optional<AdminAlertRecord> findById(String alertId);

  AdminAlertPage list(AdminAlertQuery query);

  AdminAlertRecord acknowledge(String alertId, String acknowledgedBy);
}
