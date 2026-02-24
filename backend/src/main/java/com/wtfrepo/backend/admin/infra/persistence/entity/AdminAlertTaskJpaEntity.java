package com.wtfrepo.backend.admin.infra.persistence.entity;

import com.wtfrepo.backend.admin.domain.AdminAlertSeverity;
import com.wtfrepo.backend.admin.domain.AdminAlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Persistence entity for admin notification alert tasks. */
@Getter
@Entity
@Table(name = "notification_alert_task")
public class AdminAlertTaskJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(name = "alert_type", nullable = false, length = 32)
  private AdminAlertType alertType;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 16)
  private AdminAlertSeverity severity;

  @Column(name = "target_type", length = 32)
  private String targetType;

  @Column(name = "target_id", length = 255)
  private String targetId;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  private String message;

  @Column(name = "email_sent_to", columnDefinition = "text")
  private String emailSentTo;

  @Column(name = "email_sent_at")
  private Instant emailSentAt;

  @Column(name = "is_acknowledged", nullable = false)
  private boolean acknowledged;

  @Column(name = "acknowledged_by", length = 64)
  private String acknowledgedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AdminAlertTaskJpaEntity() {}

  public static AdminAlertTaskJpaEntity create(
      AdminAlertType alertType,
      AdminAlertSeverity severity,
      String targetType,
      String targetId,
      String message) {
    Instant now = Instant.now();
    AdminAlertTaskJpaEntity entity = new AdminAlertTaskJpaEntity();
    entity.id = "alt_" + UUID.randomUUID();
    entity.alertType = alertType;
    entity.severity = severity;
    entity.targetType = targetType;
    entity.targetId = targetId;
    entity.message = message;
    entity.acknowledged = false;
    entity.createdAt = now;
    return entity;
  }

  public void setEmailSent(String emailSentTo, Instant emailSentAt) {
    this.emailSentTo = emailSentTo;
    this.emailSentAt = emailSentAt;
  }

  public void acknowledge(String acknowledgedBy) {
    this.acknowledged = true;
    this.acknowledgedBy = acknowledgedBy;
  }
}
