package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;

public record CommentContextResponse(
    String commentId, AuthorResponse author, String contentPreview, String status) {

  static CommentContextResponse from(CommentService.ContextResult result) {
    return new CommentContextResponse(
        result.commentId(),
        AuthorResponse.from(result.author()),
        result.contentPreview(),
        result.status());
  }

  public record AuthorResponse(String userId, String username, String avatarUrl) {

    static AuthorResponse from(CommentService.ContextAuthor author) {
      return new AuthorResponse(author.userId(), author.username(), author.avatarUrl());
    }
  }
}

