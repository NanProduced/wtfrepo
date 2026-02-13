package com.wtfrepo.backend.shared.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Temporary in-memory idempotency store.
 *
 * <p>TODO(M01-arena): replace with module-specific persistent stores for S0 writes.
 */
@Component
@ConditionalOnMissingBean(IdempotencyStore.class)
public class InMemoryIdempotencyStore implements IdempotencyStore<Object> {

  private final Map<String, StoredResult<Object>> store = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredResult<Object>> find(String scope, String idempotencyKey) {
    return Optional.ofNullable(store.get(buildStorageKey(scope, idempotencyKey)));
  }

  @Override
  public void save(String scope, String idempotencyKey, String requestFingerprint, Object response) {
    store.put(
        buildStorageKey(scope, idempotencyKey), new StoredResult<>(requestFingerprint, response));
  }

  private String buildStorageKey(String scope, String idempotencyKey) {
    return scope + "::" + idempotencyKey;
  }
}