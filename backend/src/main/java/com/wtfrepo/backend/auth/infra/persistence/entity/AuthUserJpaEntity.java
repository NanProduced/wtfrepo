package com.wtfrepo.backend.auth.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * Persistence entity for user profile fields managed in auth module.
 */
@Getter
@Entity
@Table(
    name = "auth_user",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_auth_user_username", columnNames = "username")
    })
public class AuthUserJpaEntity {

  /** Internal stable user id (e.g., `u_xxx`). */
  @Id
  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  /** Public username displayed in product UI. */
  @Column(name = "username", nullable = false, unique = true, length = 64)
  private String username;

  /** Whether one-time rename quota has already been consumed. */
  @Column(name = "username_changed", nullable = false)
  private boolean usernameChanged;

  /**
   * Bug balance snapshot returned by auth-facing profile APIs.
   *
   * <p>TODO(M03-economy): migrate ownership to economy wallet source-of-truth; keep this field as
   * read projection only until dedicated economy module is fully integrated.
   */
  @Column(name = "bug_balance", nullable = false)
  private long bugBalance;

  /** Creation timestamp of user profile. */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Last update timestamp of user profile. */
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AuthUserJpaEntity() {}

  public static AuthUserJpaEntity create(String userId, String username, long bugBalance) {
    Instant now = Instant.now();
    AuthUserJpaEntity entity = new AuthUserJpaEntity();
    entity.userId = userId;
    entity.username = username;
    entity.usernameChanged = false;
    entity.bugBalance = bugBalance;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void rename(String username) {
    this.username = username;
    this.usernameChanged = true;
    this.updatedAt = Instant.now();
  }

  public boolean refreshBugBalance(long bugBalance) {
    if (this.bugBalance == bugBalance) {
      return false;
    }
    this.bugBalance = bugBalance;
    this.updatedAt = Instant.now();
    return true;
  }

}
