package com.wtfrepo.backend.specimen.domain;

/** Workflow status for specimen curation lifecycle. */
public enum SpecimenStatus {
  /** Imported but not yet submitted for review. */
  DRAFT,
  /** Submitted by admin and waiting for review action. */
  PENDING,
  /** Approved and visible to users. */
  ACTIVE,
  /** Manually taken off shelf after being active (contract term: OFFLINED). */
  OFFLINED,
  /** Rejected in review; can be edited and re-submitted. */
  REJECTED
}
