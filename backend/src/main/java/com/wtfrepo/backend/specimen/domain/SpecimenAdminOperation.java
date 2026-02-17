package com.wtfrepo.backend.specimen.domain;

/** Idempotent admin write operation types in specimen module. */
public enum SpecimenAdminOperation {
  /** Admin curation submit operation. */
  SUBMIT,
  /** Admin review operation on a pending specimen. */
  REVIEW,
  /** Admin tag update operation on an active specimen. */
  TAGS_UPDATE,
  /** Admin off-shelf operation on an active specimen. */
  DEACTIVATE
}
