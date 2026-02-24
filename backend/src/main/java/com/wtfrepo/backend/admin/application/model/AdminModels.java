package com.wtfrepo.backend.admin.application.model;

import com.wtfrepo.backend.admin.domain.AdminAlertSeverity;
import com.wtfrepo.backend.admin.domain.AdminAlertType;
import com.wtfrepo.backend.admin.domain.AdminBanType;
import com.wtfrepo.backend.admin.domain.AdminRole;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketSource;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import com.wtfrepo.backend.shared.security.IssuedToken;
import java.time.Instant;
import java.util.List;

/** Shared admin models for platform workflows. */
public final class AdminModels {

  private AdminModels() {}

  public record AdminPrincipal(String userId, String username, List<String> roles) {}

  public record AdminRoleRecord(
      String userId,
      AdminRole role,
      boolean active,
      String grantedBy,
      Instant grantedAt,
      String revokedBy,
      Instant revokedAt) {}

  public record AdminBootstrapRecord(
      String adminUserId,
      String emailUsed,
      Instant bootstrappedAt,
      String ipAddress,
      String userAgent) {}

  public record AdminAuditLogEntry(
      String id,
      String operatorId,
      String action,
      String targetType,
      String targetId,
      Object beforeSnapshot,
      Object afterSnapshot,
      Object metadata,
      String requestId,
      String ipAddress,
      String userAgent,
      Instant createdAt) {}

  public record AdminAuditLogQuery(
      int page,
      int pageSize,
      String operatorId,
      String action,
      String targetType,
      String targetId) {}

  public record AdminAuditLogPage(
      List<AdminAuditLogEntry> items,
      int page,
      int pageSize,
      int total,
      int totalPages) {}

  public record AdminSafetyTicketRecord(
      String id,
      AdminSafetyTicketSource source,
      AdminSafetyTicketStatus status,
      String reporterId,
      String targetType,
      String targetId,
      String reason,
      String resolution,
      String resolvedBy,
      Instant resolvedAt,
      Instant createdAt,
      Instant updatedAt) {}

  public record AdminSafetyTicketQuery(
      int page,
      int pageSize,
      AdminSafetyTicketStatus status) {}

  public record AdminSafetyTicketPage(
      List<AdminSafetyTicketRecord> items,
      int page,
      int pageSize,
      int total,
      int totalPages) {}

  public record AdminAlertRecord(
      String id,
      AdminAlertType alertType,
      AdminAlertSeverity severity,
      String targetType,
      String targetId,
      String message,
      List<String> emailSentTo,
      Instant emailSentAt,
      boolean acknowledged,
      String acknowledgedBy,
      Instant createdAt) {}

  public record AdminAlertQuery(
      int page,
      int pageSize,
      Boolean acknowledged) {}

  public record AdminAlertPage(
      List<AdminAlertRecord> items,
      int page,
      int pageSize,
      int total,
      int totalPages) {}

  public record AdminUserBanRecord(
      String id,
      String userId,
      String username,
      AdminBanType banType,
      String reason,
      String bannedBy,
      Instant bannedAt,
      Instant expiresAt,
      String unbannedBy,
      Instant unbannedAt,
      boolean active) {}

  public record AdminUserBanQuery(
      int page,
      int pageSize,
      Boolean active) {}

  public record AdminUserBanPage(
      List<AdminUserBanRecord> items,
      int page,
      int pageSize,
      int total,
      int totalPages) {}

  public record AdminAuthResult(
      IssuedToken issuedToken,
      String userId,
      String username,
      List<String> roles) {}

  public record AdminManagerRecord(
      String userId,
      String username,
      String role,
      boolean active,
      String grantedBy,
      Instant grantedAt,
      String revokedBy,
      Instant revokedAt) {}
}
