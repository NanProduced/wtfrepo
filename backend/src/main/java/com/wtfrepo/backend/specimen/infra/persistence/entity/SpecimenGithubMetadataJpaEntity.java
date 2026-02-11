package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Persisted GitHub public metadata snapshot for a specimen.
 */
@Getter
@Entity
@Table(name = "specimen_github_metadata")
public class SpecimenGithubMetadataJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "repo_id")
  private Long repoId;

  @Column(name = "repo_html_url", length = 512)
  private String repoHtmlUrl;

  @Column(name = "owner_login", length = 128)
  private String ownerLogin;

  @Column(name = "owner_id", length = 64)
  private String ownerId;

  @Column(name = "owner_avatar_url", length = 512)
  private String ownerAvatarUrl;

  @Column(name = "owner_html_url", length = 512)
  private String ownerHtmlUrl;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "homepage", length = 512)
  private String homepage;

  @Column(name = "default_branch", length = 128)
  private String defaultBranch;

  @Column(name = "languages_json", columnDefinition = "text")
  private String languagesJson;

  @Column(name = "topics_json", columnDefinition = "text")
  private String topicsJson;

  @Column(name = "license_spdx_id", length = 64)
  private String licenseSpdxId;

  @Column(name = "license_name", length = 128)
  private String licenseName;

  /** Repository visibility, currently expected to be `public` in curation flow. */
  @Column(name = "visibility", length = 32)
  private String visibility;

  @Column(name = "archived", nullable = false)
  private boolean archived;

  @Column(name = "fork", nullable = false)
  private boolean fork;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at_remote")
  private Instant updatedAtRemote;

  @Column(name = "stargazers_count", nullable = false)
  private long stargazersCount;

  @Column(name = "forks_count", nullable = false)
  private long forksCount;

  @Column(name = "open_issues_count", nullable = false)
  private long openIssuesCount;

  @Column(name = "pushed_at")
  private Instant pushedAt;

  @Column(name = "metadata_synced_at", nullable = false)
  private Instant metadataSyncedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenGithubMetadataJpaEntity() {}

  public static SpecimenGithubMetadataJpaEntity createDraft(
      String specimenId,
      String ownerLogin,
      String ownerId,
      String ownerAvatarUrl,
      String ownerHtmlUrl,
      String repoHtmlUrl,
      String languagesJson,
      String topicsJson) {
    Instant now = Instant.now();
    SpecimenGithubMetadataJpaEntity entity = new SpecimenGithubMetadataJpaEntity();
    entity.specimenId = specimenId;
    entity.ownerLogin = ownerLogin;
    entity.ownerId = ownerId;
    entity.ownerAvatarUrl = ownerAvatarUrl;
    entity.ownerHtmlUrl = ownerHtmlUrl;
    entity.repoHtmlUrl = repoHtmlUrl;
    entity.languagesJson = languagesJson;
    entity.topicsJson = topicsJson;
    entity.metadataSyncedAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void updateLanguagesJson(String languagesJson) {
    this.languagesJson = languagesJson;
    this.updatedAt = Instant.now();
    this.metadataSyncedAt = this.updatedAt;
  }
}
