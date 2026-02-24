package com.wtfrepo.backend.comments.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * Persisted replay key for comment publish requests.
 *
 * <p>One `(user_id, client_request_id)` maps to one comment write result.
 */
@Getter
@Entity
@Table(
    name = "comment_request_idempotency",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_comment_request_idempotency_user_request",
          columnNames = {"user_id", "client_request_id"})
    })
public class CommentRequestIdempotencyJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "client_request_id", nullable = false, length = 128)
  private String clientRequestId;

  @Column(name = "comment_id", length = 64)
  private String commentId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CommentRequestIdempotencyJpaEntity() {}

  public static CommentRequestIdempotencyJpaEntity create(
      String id, String userId, String clientRequestId, String commentId) {
    CommentRequestIdempotencyJpaEntity entity = new CommentRequestIdempotencyJpaEntity();
    entity.id = id;
    entity.userId = userId;
    entity.clientRequestId = clientRequestId;
    entity.commentId = commentId;
    entity.createdAt = Instant.now();
    return entity;
  }
}

