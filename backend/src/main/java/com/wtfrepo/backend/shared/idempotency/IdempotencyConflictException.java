package com.wtfrepo.backend.shared.idempotency;

/** Raised when the same idempotency key is reused with a different request fingerprint. */
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}

