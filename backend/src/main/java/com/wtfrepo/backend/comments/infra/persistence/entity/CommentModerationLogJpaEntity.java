package com.wtfrepo.backend.comments.infra.persistence.entity;

import com.wtfrepo.backend.comments.domain.CommentModerationAction;
import com.wtfrepo.backend.comments.domain.CommentModerationActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Moderation audit log for comment governance actions. */
@Getter
@Entity
@Table(name = "comment_moderation_log")
public class CommentModerationLogJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "comment_id", nullable = false, length = 64)
  private String commentId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 16)
  private CommentModerationAction action;

  @Column(name = "reason_code", length = 64)
  private String reasonCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false, length = 16)
  private CommentModerationActorType actorType;

  @Column(name = "actor_id", length = 64)
  private String actorId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CommentModerationLogJpaEntity() {}

  public static CommentModerationLogJpaEntity create(
      String commentId,
      String specimenId,
      String userId,
      CommentModerationAction action,
      String reasonCode,
      CommentModerationActorType actorType,
      String actorId) {
    CommentModerationLogJpaEntity entity = new CommentModerationLogJpaEntity();
    entity.id = "cml_" + UUID.randomUUID();
    entity.commentId = commentId;
    entity.specimenId = specimenId;
    entity.userId = userId;
    entity.action = action;
    entity.reasonCode = reasonCode;
    entity.actorType = actorType;
    entity.actorId = actorId;
    entity.createdAt = Instant.now();
    return entity;
  }
}
