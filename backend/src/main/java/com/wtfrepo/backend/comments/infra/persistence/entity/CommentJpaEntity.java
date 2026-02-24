package com.wtfrepo.backend.comments.infra.persistence.entity;

import com.wtfrepo.backend.comments.domain.CommentAuthorRepoRoleSnapshot;
import com.wtfrepo.backend.comments.domain.CommentModerationRiskLevel;
import com.wtfrepo.backend.comments.domain.CommentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Comment aggregate persistence model.
 *
 * <p>Flyway initialization is intentionally deferred in this project stage. JPA entities are used
 * first so contracts can be stabilized before schema freeze.
 */
@Getter
@Entity
@Table(name = "comment")
public class CommentJpaEntity {

  @Id
  @Column(name = "comment_id", nullable = false, length = 64)
  private String commentId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "author_user_id", nullable = false, length = 64)
  private String authorUserId;

  @Column(name = "reply_to_comment_id", length = 64)
  private String replyToCommentId;

  @Column(name = "reply_to_user_id", length = 64)
  private String replyToUserId;

  @Column(name = "to_username_snapshot", length = 64)
  private String toUsernameSnapshot;

  @Column(name = "content_md", nullable = false, columnDefinition = "text")
  private String contentMd;

  @Column(name = "content_preview", nullable = false, length = 200)
  private String contentPreview;

  @Column(name = "stamp_codes", nullable = false, columnDefinition = "text")
  private String stampCodes;

  @Column(name = "mentioned_user_ids", nullable = false, columnDefinition = "text")
  private String mentionedUserIds;

  @Column(name = "resonance_count", nullable = false)
  private int resonanceCount;

  @Column(name = "hot_score", nullable = false, precision = 19, scale = 6)
  private BigDecimal hotScore;

  @Column(name = "is_chief_conclusion", nullable = false)
  private boolean chiefConclusion;

  @Enumerated(EnumType.STRING)
  @Column(name = "author_repo_role_snapshot", nullable = false, length = 32)
  private CommentAuthorRepoRoleSnapshot authorRepoRoleSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "moderation_risk_level", nullable = false, length = 32)
  private CommentModerationRiskLevel moderationRiskLevel;

  @Column(name = "moderation_reason_code", length = 64)
  private String moderationReasonCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private CommentStatus status;

  @Column(name = "billing_ledger_id", length = 64)
  private String billingLedgerId;

  @Column(name = "deleted_by", length = 64)
  private String deletedBy;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CommentJpaEntity() {}

  public static CommentJpaEntity create(
      String commentId,
      String specimenId,
      String authorUserId,
      String replyToCommentId,
      String replyToUserId,
      String toUsernameSnapshot,
      String contentMd,
      String contentPreview,
      String stampCodes,
      String mentionedUserIds,
      CommentAuthorRepoRoleSnapshot authorRepoRoleSnapshot,
      CommentModerationRiskLevel moderationRiskLevel,
      String moderationReasonCode,
      CommentStatus status,
      String billingLedgerId) {
    Instant now = Instant.now();
    CommentJpaEntity entity = new CommentJpaEntity();
    entity.commentId = commentId;
    entity.specimenId = specimenId;
    entity.authorUserId = authorUserId;
    entity.replyToCommentId = replyToCommentId;
    entity.replyToUserId = replyToUserId;
    entity.toUsernameSnapshot = toUsernameSnapshot;
    entity.contentMd = contentMd;
    entity.contentPreview = contentPreview;
    entity.stampCodes = stampCodes;
    entity.mentionedUserIds = mentionedUserIds;
    entity.resonanceCount = 0;
    entity.hotScore = BigDecimal.ZERO;
    entity.chiefConclusion = false;
    entity.authorRepoRoleSnapshot = authorRepoRoleSnapshot;
    entity.moderationRiskLevel = moderationRiskLevel;
    entity.moderationReasonCode = moderationReasonCode;
    entity.status = status;
    entity.billingLedgerId = billingLedgerId;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  /**
   * Applies one successful resonance action.
   *
   * <p>MVP uses a lightweight monotonic update: increment {@code resonance_count} and bump
   * {@code hot_score} by one step. A full recalculation strategy can replace this in a later phase.
   */
  public void incrementResonance() {
    this.resonanceCount += 1;
    this.hotScore = this.hotScore.add(BigDecimal.ONE);
    this.updatedAt = Instant.now();
  }

  /** Marks this comment as soft-deleted while keeping reply-chain anchors available for read flows. */
  public void markDeleted(String deletedBy) {
    this.status = CommentStatus.DELETED;
    this.deletedBy = deletedBy;
    this.deletedAt = Instant.now();
    this.chiefConclusion = false;
    this.updatedAt = Instant.now();
  }

  /** Updates comment status and refreshes the update timestamp. */
  public void updateStatus(CommentStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  /** Clears top-roast marker when moderation hides the comment. */
  public boolean clearChiefConclusion() {
    if (!this.chiefConclusion) {
      return false;
    }
    this.chiefConclusion = false;
    this.updatedAt = Instant.now();
    return true;
  }
}
