package com.wtfrepo.backend.auth.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Persistence entity for exchange idempotency replay.
 *
 * <p>Stores normalized exchange result and request fingerprint under request id.
 */
@Getter
@Entity
@Table(name = "auth_exchange_idempotency")
public class AuthExchangeIdempotencyJpaEntity {

  /** Idempotency key from request header (`X-Request-Id`). */
  @Id
  @Column(name = "request_id", nullable = false, length = 128)
  private String requestId;

  /** Deterministic fingerprint of exchange payload for conflict detection. */
  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  /** Persisted token value for replaying idempotent response. */
  @Column(name = "access_token", nullable = false, columnDefinition = "text")
  private String accessToken;

  /** Token TTL in seconds. */
  @Column(name = "token_expires_in", nullable = false)
  private long tokenExpiresIn;

  /** Token absolute expiration instant. */
  @Column(name = "token_expires_at", nullable = false)
  private Instant tokenExpiresAt;

  /** User id returned in exchange response. */
  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  /** Username returned in exchange response. */
  @Column(name = "username", nullable = false, length = 64)
  private String username;

  /** Username rename state returned in exchange response. */
  @Column(name = "username_changed", nullable = false)
  private boolean usernameChanged;

  /** Bug balance returned in exchange response. */
  @Column(name = "bug_balance", nullable = false)
  private long bugBalance;

  /** Whether user was newly created in this exchange. */
  @Column(name = "is_new_user", nullable = false)
  private boolean isNewUser;

  /** Initial bug grant included for new users. */
  @Column(name = "initial_bug_grant", nullable = false)
  private int initialBugGrant;

  /** Record creation timestamp. */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuthExchangeIdempotencyJpaEntity() {}

  public static AuthExchangeIdempotencyJpaEntity of(
      String requestId,
      String requestFingerprint,
      String accessToken,
      long tokenExpiresIn,
      Instant tokenExpiresAt,
      String userId,
      String username,
      boolean usernameChanged,
      long bugBalance,
      boolean isNewUser,
      int initialBugGrant) {
    AuthExchangeIdempotencyJpaEntity entity = new AuthExchangeIdempotencyJpaEntity();
    entity.requestId = requestId;
    entity.requestFingerprint = requestFingerprint;
    entity.accessToken = accessToken;
    entity.tokenExpiresIn = tokenExpiresIn;
    entity.tokenExpiresAt = tokenExpiresAt;
    entity.userId = userId;
    entity.username = username;
    entity.usernameChanged = usernameChanged;
    entity.bugBalance = bugBalance;
    entity.isNewUser = isNewUser;
    entity.initialBugGrant = initialBugGrant;
    entity.createdAt = Instant.now();
    return entity;
  }
}
