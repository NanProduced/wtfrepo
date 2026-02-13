package com.wtfrepo.backend.shared.idempotency;

import java.util.Optional;

/**
 * Generic store abstraction for idempotency replay and conflict detection.
 *
 * @param <T> stored response type
 */
public interface IdempotencyStore<T> {

  Optional<StoredResult<T>> find(String scope, String idempotencyKey);

  void save(String scope, String idempotencyKey, String requestFingerprint, T response);

  record StoredResult<T>(String requestFingerprint, T response) {}
}
