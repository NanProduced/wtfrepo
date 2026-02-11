package com.wtfrepo.backend.specimen.domain;

/** Idempotent admin write operation types in specimen module. */
public enum SpecimenAdminOperation {
  /** Admin curation submit operation. */
  SUBMIT,
  /** Admin review operation on a pending specimen. */
  REVIEW
}
