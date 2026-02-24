package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;
import java.time.Instant;
import java.util.List;

public record CommentListResponse(List<CommentListItemResponse> items, String nextCursor) {

  static CommentListResponse from(CommentService.ListResult result) {
    List<CommentListItemResponse> mappedItems =
        result.items().stream().map(CommentListItemResponse::from).toList();
    return new CommentListResponse(mappedItems, result.nextCursor());
  }

  public record CommentListItemResponse(
      String commentId,
      String specimenId,
      String authorUserId,
      String contentPreview,
      int resonanceCount,
      boolean isChiefConclusion,
      String status,
      Instant createdAt,
      Instant updatedAt) {

    static CommentListItemResponse from(CommentService.ListItem item) {
      return new CommentListItemResponse(
          item.commentId(),
          item.specimenId(),
          item.authorUserId(),
          item.contentPreview(),
          item.resonanceCount(),
          item.isChiefConclusion(),
          item.status(),
          item.createdAt(),
          item.updatedAt());
    }
  }
}

