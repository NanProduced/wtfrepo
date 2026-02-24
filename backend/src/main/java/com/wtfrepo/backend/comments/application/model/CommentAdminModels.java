package com.wtfrepo.backend.comments.application.model;

import java.time.Instant;
import java.util.List;

/** Admin-facing comment moderation models. */
public final class CommentAdminModels {

  private CommentAdminModels() {}

  public record ModerationQueueItem(
      String commentId,
      String specimenId,
      String authorUserId,
      String contentPreview,
      String status,
      String moderationRiskLevel,
      String moderationReasonCode,
      Instant createdAt,
      Instant updatedAt) {}

  public record ModerationQueuePage(
      List<ModerationQueueItem> items,
      int page,
      int pageSize,
      int total,
      int totalPages) {}

  public record ModerationSnapshot(
      String commentId,
      String specimenId,
      String authorUserId,
      String status,
      boolean chiefConclusion,
      int resonanceCount) {}

  public record ModerationActionResult(
      ModerationSnapshot before, ModerationSnapshot after, int refundAmount) {}
}
