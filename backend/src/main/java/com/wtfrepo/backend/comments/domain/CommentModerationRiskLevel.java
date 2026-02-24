package com.wtfrepo.backend.comments.domain;

/** Synchronous moderation risk result for publish requests. */
public enum CommentModerationRiskLevel {
  /** Content can be published directly. */
  PASS,
  /** Content needs manual review before becoming visible. */
  REVIEW,
  /** Content violates policy and should be blocked immediately. */
  REJECT
}

