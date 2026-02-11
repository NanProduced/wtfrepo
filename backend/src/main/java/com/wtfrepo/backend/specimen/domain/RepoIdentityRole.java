package com.wtfrepo.backend.specimen.domain;

/** Relationship role between GitHub account and specimen repository. */
public enum RepoIdentityRole {
  /** Repository owner account. */
  OWNER,
  /** Project maintainer account that is not owner. */
  MAINTAINER,
  /** Contributor account without maintainer authority. */
  CONTRIBUTOR
}
