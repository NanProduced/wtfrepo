package com.wtfrepo.backend.comments.application;

import com.wtfrepo.backend.comments.domain.CommentAuthorRepoRoleSnapshot;

/** Resolves author repository role snapshots for comment publishing. */
public interface CommentAuthorRoleResolver {

  CommentAuthorRepoRoleSnapshot resolveRole(String specimenId, String userId);
}
