package com.wtfrepo.backend.arena.application;

/** Arena API rate-limiter abstraction. */
public interface ArenaRateLimiter {

  boolean allowAnonymousDuel(String clientIp);

  boolean allowAuthenticatedDuelByUser(String userId);

  boolean allowAuthenticatedDuelByIp(String clientIp);
}

