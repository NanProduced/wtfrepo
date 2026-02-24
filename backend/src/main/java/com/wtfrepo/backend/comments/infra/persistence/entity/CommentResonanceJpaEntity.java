package com.wtfrepo.backend.comments.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * User resonance relation for one comment.
 *
 * <p>Unique `(comment_id, user_id)` keeps resonance action idempotent.
 */
@Getter
@Entity
@Table(
    name = "comment_resonance",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_comment_resonance_comment_user",
          columnNames = {"comment_id", "user_id"})
    })
public class CommentResonanceJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "comment_id", nullable = false, length = 64)
  private String commentId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CommentResonanceJpaEntity() {}

  public static CommentResonanceJpaEntity create(
      String id, String commentId, String specimenId, String userId) {
    CommentResonanceJpaEntity entity = new CommentResonanceJpaEntity();
    entity.id = id;
    entity.commentId = commentId;
    entity.specimenId = specimenId;
    entity.userId = userId;
    entity.createdAt = Instant.now();
    return entity;
  }
}

