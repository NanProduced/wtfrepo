package com.wtfrepo.backend.admin.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Persistence entity for admin bootstrap record. */
@Getter
@Entity
@Table(name = "admin_bootstrap_record")
public class AdminBootstrapRecordJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "admin_user_id", nullable = false, length = 64)
  private String adminUserId;

  @Column(name = "email_used", nullable = false, length = 255)
  private String emailUsed;

  @Column(name = "bootstrapped_at", nullable = false)
  private Instant bootstrappedAt;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "text")
  private String userAgent;

  protected AdminBootstrapRecordJpaEntity() {}

  public static AdminBootstrapRecordJpaEntity create(
      String adminUserId,
      String emailUsed,
      String ipAddress,
      String userAgent,
      Instant bootstrappedAt) {
    AdminBootstrapRecordJpaEntity entity = new AdminBootstrapRecordJpaEntity();
    entity.id = "abr_" + UUID.randomUUID();
    entity.adminUserId = adminUserId;
    entity.emailUsed = emailUsed;
    entity.ipAddress = ipAddress;
    entity.userAgent = userAgent;
    entity.bootstrappedAt = bootstrappedAt;
    return entity;
  }
}
