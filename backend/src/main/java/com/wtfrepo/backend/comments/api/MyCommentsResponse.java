package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;
import java.time.Instant;
import java.util.List;

public record MyCommentsResponse(List<Item> items, String nextCursor, boolean hasMore) {

  public static MyCommentsResponse from(CommentService.MyCommentsResult result) {
    List<Item> items =
        result.items().stream()
            .map(
                item ->
                    new Item(
                        item.commentId(),
                        new Specimen(item.specimen().specimenId(), item.specimen().repoFullName()),
                        item.contentPreview(),
                        item.status(),
                        item.resonanceCount(),
                        item.createdAt(),
                        item.locateUrl()))
            .toList();
    return new MyCommentsResponse(items, result.nextCursor(), result.hasMore());
  }

  public record Item(
      String commentId,
      Specimen specimen,
      String contentPreview,
      String status,
      int resonanceCount,
      Instant createdAt,
      String locateUrl) {}

  public record Specimen(String specimenId, String repoFullName) {}
}

