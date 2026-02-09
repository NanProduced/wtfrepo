package com.wtfrepo.backend.auth.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * Persistence entity for one-time oauthState consumption.
 */
@Getter
@Entity
@Table(name = "auth_oauth_state")
public class OAuthStateJpaEntity {

  /** Raw oauthState value, consumed exactly once. */
  @Id
  @Column(name = "state", nullable = false, length = 255)
  private String state;

  /** Consumption timestamp. */
  @Column(name = "consumed_at", nullable = false)
  private Instant consumedAt;

  protected OAuthStateJpaEntity() {}

  public static OAuthStateJpaEntity consumed(String state) {
    OAuthStateJpaEntity entity = new OAuthStateJpaEntity();
    entity.state = state;
    entity.consumedAt = Instant.now();
    return entity;
  }

}
