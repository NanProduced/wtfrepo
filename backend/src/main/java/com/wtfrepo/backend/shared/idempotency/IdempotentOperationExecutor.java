package com.wtfrepo.backend.shared.idempotency;

import java.util.function.Supplier;

/**
 * Executes write operations with idempotency replay and conflict guard.
 *
 * <p>Implementations should guarantee that the same `(scope, idempotencyKey)` never commits twice.
 */
public interface IdempotentOperationExecutor {

  <T> IdempotentOperationResult<T> execute(
      String scope,
      String idempotencyKey,
      String requestFingerprint,
      Supplier<T> operation);
}
