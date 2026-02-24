package com.wtfrepo.backend.admin.infra.persistence.entity;

import com.wtfrepo.backend.admin.domain.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Persistence entity for admin role assignments. */
@Getter
@Entity
@Table(
    name = "admin_user_role",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_admin_user_role", columnNames = {"user_id", "role"})
    })
public class AdminUserRoleJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private AdminRole role;

  @Column(name = "granted_by", length = 64)
  private String grantedBy;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected AdminUserRoleJpaEntity() {}

  public static AdminUserRoleJpaEntity create(
      String userId, AdminRole role, String grantedBy) {
    AdminUserRoleJpaEntity entity = new AdminUserRoleJpaEntity();
    entity.id = "aur_" + UUID.randomUUID();
    entity.userId = userId;
    entity.role = role;
    entity.grantedBy = grantedBy;
    entity.grantedAt = Instant.now();
    entity.active = true;
    return entity;
  }

  public void activate(String grantedBy) {
    this.grantedBy = grantedBy;
    this.grantedAt = Instant.now();
    this.revokedAt = null;
    this.active = true;
  }

  public void revoke() {
    this.revokedAt = Instant.now();
    this.active = false;
  }
}
