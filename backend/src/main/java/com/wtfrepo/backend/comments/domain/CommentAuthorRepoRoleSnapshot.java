package com.wtfrepo.backend.comments.domain;

/**
 * Author role snapshot within the target specimen repository.
 *
 * <p>M07 identity contract is not finalized in this stage, so MVP defaults to {@link #NONE} and
 * keeps this field as forward-compatible storage.
 */
public enum CommentAuthorRepoRoleSnapshot {
  NONE,
  OWNER,
  MAINTAINER
}

