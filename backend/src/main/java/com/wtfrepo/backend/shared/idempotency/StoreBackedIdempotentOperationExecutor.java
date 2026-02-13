package com.wtfrepo.backend.shared.idempotency;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation for idempotent execution using an {@link IdempotencyStore}.
 */
@Component
public class StoreBackedIdempotentOperationExecutor implements IdempotentOperationExecutor {

  private final IdempotencyStore<Object> idempotencyStore;

  @SuppressWarnings("unchecked")
  public StoreBackedIdempotentOperationExecutor(IdempotencyStore<?> idempotencyStore) {
    this.idempotencyStore = (IdempotencyStore<Object>) idempotencyStore;
  }

  @Override
  @Transactional
  public <T> IdempotentOperationResult<T> execute(
      String scope,
      String idempotencyKey,
      String requestFingerprint,
      Supplier<T> operation) {
    var existing = idempotencyStore.find(scope, idempotencyKey);
    if (existing.isPresent()) {
      var stored = existing.get();
      if (!Objects.equals(stored.requestFingerprint(), requestFingerprint)) {
        throw new IdempotencyConflictException(
            "Idempotency key conflict for scope=" + scope + ", idempotencyKey=" + idempotencyKey);
      }
      @SuppressWarnings("unchecked")
      T response = (T) stored.response();
      return new IdempotentOperationResult<>(response, true);
    }

    T response = operation.get();
    idempotencyStore.save(scope, idempotencyKey, requestFingerprint, response);
    return new IdempotentOperationResult<>(response, false);
  }
}
