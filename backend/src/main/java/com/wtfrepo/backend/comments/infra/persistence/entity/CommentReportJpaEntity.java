package com.wtfrepo.backend.comments.infra.persistence.entity;

import com.wtfrepo.backend.comments.domain.CommentReportReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;

/**
 * Report record for one comment moderation complaint.
 *
 * <p>Duplicate reports are blocked by `(comment_id, reporter_user_id, reason_code, date_bucket)`
 * uniqueness within one date bucket.
 */
@Getter
@Entity
@Table(
    name = "comment_report",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_comment_report_comment_user_reason_bucket",
          columnNames = {"comment_id", "reporter_user_id", "reason_code", "date_bucket"})
    })
public class CommentReportJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "comment_id", nullable = false, length = 64)
  private String commentId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "reporter_user_id", nullable = false, length = 64)
  private String reporterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", nullable = false, length = 32)
  private CommentReportReasonCode reasonCode;

  @Column(name = "message", length = 1000)
  private String message;

  @Column(name = "ticket_id", length = 64)
  private String ticketId;

  @Column(name = "date_bucket", nullable = false)
  private LocalDate dateBucket;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CommentReportJpaEntity() {}

  public static CommentReportJpaEntity create(
      String id,
      String commentId,
      String specimenId,
      String reporterUserId,
      CommentReportReasonCode reasonCode,
      String message,
      LocalDate dateBucket,
      String ticketId) {
    CommentReportJpaEntity entity = new CommentReportJpaEntity();
    entity.id = id;
    entity.commentId = commentId;
    entity.specimenId = specimenId;
    entity.reporterUserId = reporterUserId;
    entity.reasonCode = reasonCode;
    entity.message = message;
    entity.ticketId = ticketId;
    entity.dateBucket = dateBucket;
    entity.createdAt = Instant.now();
    return entity;
  }
}
