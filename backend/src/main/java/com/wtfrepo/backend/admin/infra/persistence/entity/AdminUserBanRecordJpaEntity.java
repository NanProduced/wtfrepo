package com.wtfrepo.backend.admin.infra.persistence.entity;

import com.wtfrepo.backend.admin.domain.AdminBanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Persistence entity for admin user ban records. */
@Getter
@Entity
@Table(name = "user_ban_record")
public class AdminUserBanRecordJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "ban_type", nullable = false, length = 32)
  private AdminBanType banType;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  private String reason;

  @Column(name = "banned_by", nullable = false, length = 64)
  private String bannedBy;

  @Column(name = "banned_at", nullable = false)
  private Instant bannedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "unbanned_by", length = 64)
  private String unbannedBy;

  @Column(name = "unbanned_at")
  private Instant unbannedAt;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected AdminUserBanRecordJpaEntity() {}

  public static AdminUserBanRecordJpaEntity create(
      String userId,
      AdminBanType banType,
      String reason,
      String bannedBy,
      Instant expiresAt) {
    AdminUserBanRecordJpaEntity entity = new AdminUserBanRecordJpaEntity();
    entity.id = "ban_" + UUID.randomUUID();
    entity.userId = userId;
    entity.banType = banType;
    entity.reason = reason;
    entity.bannedBy = bannedBy;
    entity.bannedAt = Instant.now();
    entity.expiresAt = expiresAt;
    entity.active = true;
    return entity;
  }

  public void unban(String unbannedBy) {
    this.unbannedBy = unbannedBy;
    this.unbannedAt = Instant.now();
    this.active = false;
  }

  public static AdminUserBanRecordJpaEntity fromRecord(
      String id,
      String userId,
      AdminBanType banType,
      String reason,
      String bannedBy,
      Instant bannedAt,
      Instant expiresAt,
      String unbannedBy,
      Instant unbannedAt,
      boolean active) {
    AdminUserBanRecordJpaEntity entity = new AdminUserBanRecordJpaEntity();
    entity.id = id;
    entity.userId = userId;
    entity.banType = banType;
    entity.reason = reason;
    entity.bannedBy = bannedBy;
    entity.bannedAt = bannedAt;
    entity.expiresAt = expiresAt;
    entity.unbannedBy = unbannedBy;
    entity.unbannedAt = unbannedAt;
    entity.active = active;
    return entity;
  }
}
