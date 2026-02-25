package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketRecord;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketSource;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import java.time.Instant;
import java.util.Optional;

/** Persistence port for admin safety ticket workflows. */
public interface AdminSafetyTicketStore {

  Optional<AdminSafetyTicketRecord> findById(String ticketId);

  Optional<AdminSafetyTicketRecord> findLatestActiveByTarget(String targetType, String targetId);

  AdminSafetyTicketRecord create(
      AdminSafetyTicketSource source,
      String reporterId,
      String targetType,
      String targetId,
      String reason,
      String ticketId);

  AdminSafetyTicketPage list(AdminSafetyTicketQuery query);

  AdminSafetyTicketRecord updateStatus(
      String ticketId,
      AdminSafetyTicketStatus status,
      String resolution,
      String resolvedBy,
      Instant resolvedAt,
      Instant updatedAt);
}
