package com.wtfrepo.backend.comments.domain;

/** Lifecycle state of one comment record. */
public enum CommentStatus {
  /** Publicly visible comment. */
  ACTIVE,
  /** Waiting for manual moderation decision. */
  PENDING_REVIEW,
  /** Hidden by moderation or governance action. */
  BLOCKED,
  /** Soft-deleted comment that keeps reply chain anchor. */
  DELETED
}

