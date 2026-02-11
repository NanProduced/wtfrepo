package com.wtfrepo.backend.specimen.infra.persistence.entity;

import com.wtfrepo.backend.specimen.domain.RepoIdentityRole;
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
 * Captures owner/contributor identities for comment badge binding.
 */
@Getter
@Entity
@Table(
    name = "specimen_repo_identity",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_specimen_repo_identity_user",
          columnNames = {"specimen_id", "github_user_id"})
    })
public class SpecimenRepoIdentityJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "github_user_id", nullable = false, length = 64)
  private String githubUserId;

  @Column(name = "github_login", nullable = false, length = 128)
  private String githubLogin;

  @Column(name = "github_avatar_url", length = 512)
  private String githubAvatarUrl;

  @Column(name = "github_html_url", length = 512)
  private String githubHtmlUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private RepoIdentityRole role;

  /** Identity source used for auditability and future reconciliation. */
  @Column(name = "source", nullable = false, length = 32)
  private String source;

  /**
   * Soft active flag to preserve historical records while allowing current relation filtering.
   */
  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "contributions")
  private Integer contributions;

  @Column(name = "synced_at", nullable = false)
  private Instant syncedAt;

  protected SpecimenRepoIdentityJpaEntity() {}

  public static SpecimenRepoIdentityJpaEntity of(
      String specimenId,
      String githubUserId,
      String githubLogin,
      String githubAvatarUrl,
      String githubHtmlUrl,
      RepoIdentityRole role,
      Integer contributions) {
    SpecimenRepoIdentityJpaEntity entity = new SpecimenRepoIdentityJpaEntity();
    entity.id = "rid_" + UUID.randomUUID();
    entity.specimenId = specimenId;
    entity.githubUserId = githubUserId;
    entity.githubLogin = githubLogin;
    entity.githubAvatarUrl = githubAvatarUrl;
    entity.githubHtmlUrl = githubHtmlUrl;
    entity.role = role;
    entity.source = "ADMIN_MANUAL";
    entity.active = true;
    entity.contributions = contributions;
    entity.syncedAt = Instant.now();
    return entity;
  }
}
