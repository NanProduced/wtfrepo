package com.wtfrepo.backend.comments.domain;

/** Moderation actions recorded for comment governance. */
public enum CommentModerationAction {
  PASS,
  REVIEW,
  REJECT,
  BLOCK,
  UNBLOCK,
  DELETE,
  APPROVE
}
