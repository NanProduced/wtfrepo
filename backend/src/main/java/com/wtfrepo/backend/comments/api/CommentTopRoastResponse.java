package com.wtfrepo.backend.comments.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wtfrepo.backend.comments.application.CommentService;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentTopRoastResponse(
    boolean hasTopRoast,
    String commentId,
    TopRoastAuthor author,
    String contentPreview,
    Integer resonanceCount) {

  public static CommentTopRoastResponse from(CommentService.TopRoastResult result) {
    if (!result.hasTopRoast()) {
      return new CommentTopRoastResponse(false, null, null, null, null);
    }
    return new CommentTopRoastResponse(
        true,
        result.commentId(),
        new TopRoastAuthor(
            result.author().userId(), result.author().username(), result.author().avatarUrl()),
        result.contentPreview(),
        result.resonanceCount());
  }

  public record TopRoastAuthor(String userId, String username, String avatarUrl) {}
}

