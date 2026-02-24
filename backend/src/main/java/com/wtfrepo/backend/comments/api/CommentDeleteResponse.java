package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;

public record CommentDeleteResponse(boolean deleted, int refundDelta) {

  static CommentDeleteResponse from(CommentService.DeleteResult result) {
    return new CommentDeleteResponse(result.deleted(), result.refundDelta());
  }
}

