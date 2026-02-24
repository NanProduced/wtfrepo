package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;

public record CommentResonanceResponse(String commentId, int resonanceCount, boolean resonated) {

  static CommentResonanceResponse from(CommentService.ResonanceResult result) {
    return new CommentResonanceResponse(
        result.commentId(), result.resonanceCount(), result.resonated());
  }
}

