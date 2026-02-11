package com.wtfrepo.backend.specimen.infra.persistence.entity;

import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Aggregate root entity for curated specimen records.
 *
 * <p>This table keeps relatively stable specimen core fields. Frequently-changing arena metrics are
 * stored in {@link SpecimenArenaMetricsJpaEntity} to reduce write contention and improve
 * maintainability between M04 (specimen) and M01 (arena).
 */
@Getter
@Entity
@Table(name = "specimen")
public class SpecimenJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "repo_full_name", nullable = false, length = 256)
  private String repoFullName;

  @Column(name = "github_url", nullable = false, length = 512)
  private String githubUrl;

  /**
   * Internal admin note, not intended for public rendering.
   *
   * <p>This field is used for curation workflow context only and must never be exposed from
   * user-facing APIs.
   */
  @Column(name = "note", columnDefinition = "text")
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private SpecimenStatus status;

  @Column(name = "reviewed_by", length = 64)
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenJpaEntity() {}

  public static SpecimenJpaEntity createDraft(String specimenId, String repoFullName, String githubUrl) {
    Instant now = Instant.now();
    SpecimenJpaEntity entity = new SpecimenJpaEntity();
    entity.specimenId = specimenId;
    entity.repoFullName = repoFullName;
    entity.githubUrl = githubUrl;
    entity.status = SpecimenStatus.DRAFT;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void submit(String note) {
    this.note = note;
    this.status = SpecimenStatus.PENDING;
    this.updatedAt = Instant.now();
  }

  public void approve(String reviewer) {
    Instant now = Instant.now();
    this.status = SpecimenStatus.ACTIVE;
    this.reviewedBy = reviewer;
    this.reviewedAt = now;
    this.updatedAt = now;
  }

  public void reject(String reviewer) {
    Instant now = Instant.now();
    this.status = SpecimenStatus.REJECTED;
    this.reviewedBy = reviewer;
    this.reviewedAt = now;
    this.updatedAt = now;
  }
}
