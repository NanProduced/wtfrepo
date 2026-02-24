package com.wtfrepo.backend.admin.infra.persistence.entity;

import com.wtfrepo.backend.admin.domain.AdminSafetyTicketSource;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Persistence entity for safety tickets. */
@Getter
@Entity
@Table(name = "safety_ticket")
public class AdminSafetyTicketJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 32)
  private AdminSafetyTicketSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private AdminSafetyTicketStatus status;

  @Column(name = "reporter_id", length = 64)
  private String reporterId;

  @Column(name = "target_type", nullable = false, length = 32)
  private String targetType;

  @Column(name = "target_id", nullable = false, length = 255)
  private String targetId;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  private String reason;

  @Column(name = "resolution", columnDefinition = "text")
  private String resolution;

  @Column(name = "resolved_by", length = 64)
  private String resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AdminSafetyTicketJpaEntity() {}

  public static AdminSafetyTicketJpaEntity create(
      AdminSafetyTicketSource source,
      String reporterId,
      String targetType,
      String targetId,
      String reason) {
    Instant now = Instant.now();
    AdminSafetyTicketJpaEntity entity = new AdminSafetyTicketJpaEntity();
    entity.id = "tkt_" + UUID.randomUUID();
    entity.source = source;
    entity.status = AdminSafetyTicketStatus.OPEN;
    entity.reporterId = reporterId;
    entity.targetType = targetType;
    entity.targetId = targetId;
    entity.reason = reason;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public static AdminSafetyTicketJpaEntity createWithId(
      String id,
      AdminSafetyTicketSource source,
      String reporterId,
      String targetType,
      String targetId,
      String reason) {
    Instant now = Instant.now();
    AdminSafetyTicketJpaEntity entity = new AdminSafetyTicketJpaEntity();
    entity.id = id;
    entity.source = source;
    entity.status = AdminSafetyTicketStatus.OPEN;
    entity.reporterId = reporterId;
    entity.targetType = targetType;
    entity.targetId = targetId;
    entity.reason = reason;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void updateStatus(
      AdminSafetyTicketStatus status,
      String resolution,
      String resolvedBy,
      Instant resolvedAt,
      Instant updatedAt) {
    this.status = status;
    if (resolution != null) {
      this.resolution = resolution;
    }
    if (resolvedBy != null) {
      this.resolvedBy = resolvedBy;
    }
    if (resolvedAt != null) {
      this.resolvedAt = resolvedAt;
    }
    this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
  }
}
