package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminBootstrapRecord;
import java.util.Optional;

/** Persistence port for admin bootstrap records. */
public interface AdminBootstrapStore {

  Optional<AdminBootstrapRecord> latest();

  boolean exists();

  AdminBootstrapRecord save(AdminBootstrapRecord record);
}
