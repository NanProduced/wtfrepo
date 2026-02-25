package com.wtfrepo.backend.shared.security;

import java.time.Instant;
import java.util.Optional;

/** Provides access to active user bans for auth/security enforcement. */
public interface UserBanPolicy {

  Optional<UserBanSnapshot> findActiveBan(String userId);

  record UserBanSnapshot(
      String userId,
      String banType,
      String reason,
      Instant bannedAt,
      Instant expiresAt) {}
}
