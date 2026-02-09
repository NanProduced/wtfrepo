package com.wtfrepo.backend.auth.application;

public interface AuthRateLimiter {

  boolean allowExchange(String key);

  boolean allowRename(String key);
}
