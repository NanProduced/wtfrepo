package com.wtfrepo.backend.auth.infra.persistence.entity;

import com.wtfrepo.backend.auth.domain.OAuthProvider;
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

/**
 * Persistence entity for mapping internal user to provider identity (`provider`, `sub`).
 */
@Getter
@Entity
@Table(
    name = "auth_identity",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_auth_identity_provider_subject",
          columnNames = {"provider", "provider_subject"})
    })
public class AuthIdentityJpaEntity {

  /** Internal identity record id. */
  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  /** Linked internal user id. */
  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  /** OAuth provider name. */
  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 32)
  private OAuthProvider provider;

  /** Upstream provider subject (`sub`) claim. */
  @Column(name = "provider_subject", nullable = false, length = 128)
  private String providerSubject;

  /** Timestamp when the identity mapping was created. */
  @Column(name = "linked_at", nullable = false)
  private Instant linkedAt;

  protected AuthIdentityJpaEntity() {}

  public static AuthIdentityJpaEntity create(
      String userId, OAuthProvider provider, String providerSubject) {
    AuthIdentityJpaEntity entity = new AuthIdentityJpaEntity();
    entity.id = "ai_" + UUID.randomUUID();
    entity.userId = userId;
    entity.provider = provider;
    entity.providerSubject = providerSubject;
    entity.linkedAt = Instant.now();
    return entity;
  }

}
