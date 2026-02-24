package com.wtfrepo.backend.admin.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Persistence entity for admin audit logs. */
@Getter
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLogJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "operator_id", nullable = false, length = 64)
  private String operatorId;

  @Column(name = "action", nullable = false, length = 64)
  private String action;

  @Column(name = "target_type", nullable = false, length = 64)
  private String targetType;

  @Column(name = "target_id", nullable = false, length = 255)
  private String targetId;

  @Column(name = "before_snapshot", columnDefinition = "text")
  private String beforeSnapshot;

  @Column(name = "after_snapshot", columnDefinition = "text")
  private String afterSnapshot;

  @Column(name = "metadata", columnDefinition = "text")
  private String metadata;

  @Column(name = "request_id", length = 100)
  private String requestId;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "text")
  private String userAgent;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AdminAuditLogJpaEntity() {}

  public static AdminAuditLogJpaEntity create(
      String id,
      String operatorId,
      String action,
      String targetType,
      String targetId,
      String beforeSnapshot,
      String afterSnapshot,
      String metadata,
      String requestId,
      String ipAddress,
      String userAgent,
      Instant createdAt) {
    AdminAuditLogJpaEntity entity = new AdminAuditLogJpaEntity();
    entity.id = id != null ? id : "aal_" + UUID.randomUUID();
    entity.operatorId = operatorId;
    entity.action = action;
    entity.targetType = targetType;
    entity.targetId = targetId;
    entity.beforeSnapshot = beforeSnapshot;
    entity.afterSnapshot = afterSnapshot;
    entity.metadata = metadata;
    entity.requestId = requestId;
    entity.ipAddress = ipAddress;
    entity.userAgent = userAgent;
    entity.createdAt = createdAt != null ? createdAt : Instant.now();
    return entity;
  }
}
