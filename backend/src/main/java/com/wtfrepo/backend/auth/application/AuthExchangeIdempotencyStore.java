package com.wtfrepo.backend.auth.application;

import java.util.Optional;

public interface AuthExchangeIdempotencyStore {

  Optional<StoredExchangeResult> find(String idempotencyKey);

  void save(String idempotencyKey, String requestFingerprint, AuthService.ExchangeResult result);

  record StoredExchangeResult(String requestFingerprint, AuthService.ExchangeResult result) {}
}
