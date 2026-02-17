package com.wtfrepo.backend.shared.outbox;

/** Delivery lifecycle state for outbox events. */
public enum OutboxEventStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
