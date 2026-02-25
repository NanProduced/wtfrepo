package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Lightweight community metrics for specimen detail rendering.
 *
 * <p>Comment counts and top-roast summary are updated via outbox consumers to avoid
 * cross-module query coupling.
 */
@Getter
@Entity
@Table(name = "specimen_community_metrics")
public class SpecimenCommunityMetricsJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "comment_count", nullable = false)
  private long commentCount;

  @Column(name = "top_roast_comment_id", length = 64)
  private String topRoastCommentId;

  @Column(name = "top_roast_resonance_count", nullable = false)
  private int topRoastResonanceCount;

  @Column(name = "top_roast_chief_conclusion", nullable = false)
  private boolean topRoastChiefConclusion;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenCommunityMetricsJpaEntity() {}

  public static SpecimenCommunityMetricsJpaEntity createDefault(String specimenId) {
    SpecimenCommunityMetricsJpaEntity entity = new SpecimenCommunityMetricsJpaEntity();
    entity.specimenId = specimenId;
    entity.commentCount = 0L;
    entity.topRoastResonanceCount = 0;
    entity.topRoastChiefConclusion = false;
    entity.updatedAt = Instant.now();
    return entity;
  }

  public void applyCommentCountDelta(long delta) {
    long next = this.commentCount + delta;
    this.commentCount = Math.max(0L, next);
    this.updatedAt = Instant.now();
  }

  public void updateTopRoast(String commentId, int resonanceCount, boolean chiefConclusion) {
    this.topRoastCommentId = commentId;
    this.topRoastResonanceCount = Math.max(0, resonanceCount);
    this.topRoastChiefConclusion = chiefConclusion;
    this.updatedAt = Instant.now();
  }

  public void clearTopRoast() {
    this.topRoastCommentId = null;
    this.topRoastResonanceCount = 0;
    this.topRoastChiefConclusion = false;
    this.updatedAt = Instant.now();
  }
}
