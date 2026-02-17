package com.wtfrepo.backend.auth.infra.support;

import com.wtfrepo.backend.auth.application.AuthExchangeIdempotencyStore;
import com.wtfrepo.backend.auth.application.AuthService;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthExchangeIdempotencyJpaRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** In-memory idempotency replay store fallback for non-JPA contexts. */
@Component
@Primary
@ConditionalOnMissingBean(AuthExchangeIdempotencyJpaRepository.class)
public class InMemoryAuthExchangeIdempotencyStore implements AuthExchangeIdempotencyStore {

  private final Map<String, StoredExchangeResult> entries = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredExchangeResult> find(String idempotencyKey) {
    return Optional.ofNullable(entries.get(idempotencyKey));
  }

  @Override
  public void save(String idempotencyKey, String requestFingerprint, AuthService.ExchangeResult result) {
    entries.put(idempotencyKey, new StoredExchangeResult(requestFingerprint, result));
  }
}
