package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;
import java.time.Instant;

public record CommentPublishResponse(
    String commentId,
    String specimenId,
    String status,
    int bugCost,
    long balanceAfter,
    String billingLedgerId,
    Instant createdAt) {

  static CommentPublishResponse from(CommentService.PublishResult result) {
    return new CommentPublishResponse(
        result.commentId(),
        result.specimenId(),
        result.status(),
        result.bugCost(),
        result.balanceAfter(),
        result.billingLedgerId(),
        result.createdAt());
  }
}

