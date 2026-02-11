package com.wtfrepo.backend.specimen.domain;

/** Review action accepted by admin review endpoint. */
public enum SpecimenReviewAction {
  /** Approve a pending specimen and activate it. */
  APPROVE,
  /** Reject a pending specimen and move it back to rejected state. */
  REJECT
}
