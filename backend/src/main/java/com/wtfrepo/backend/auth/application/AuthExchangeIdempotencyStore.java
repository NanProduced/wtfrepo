package com.wtfrepo.backend.auth.application;

import java.util.Optional;

public interface AuthExchangeIdempotencyStore {

  Optional<StoredExchangeResult> find(String requestId);

  void save(String requestId, String requestFingerprint, AuthService.ExchangeResult result);

  record StoredExchangeResult(String requestFingerprint, AuthService.ExchangeResult result) {}
}
