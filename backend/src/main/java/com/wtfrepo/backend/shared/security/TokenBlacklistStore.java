package com.wtfrepo.backend.shared.security;

import java.time.Duration;

/** Persistence contract for JWT blacklist entries. */
public interface TokenBlacklistStore {

  void blacklist(String tokenId, Duration ttl);

  boolean isBlacklisted(String tokenId);
}
