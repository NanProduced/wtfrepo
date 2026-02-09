package com.wtfrepo.backend.auth.infra.support;

import com.wtfrepo.backend.auth.application.support.AuthConstants;
import com.wtfrepo.backend.auth.application.AuthRateLimitProperties;
import com.wtfrepo.backend.auth.application.AuthRateLimiter;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisAuthRateLimiter implements AuthRateLimiter {

  private final StringRedisTemplate redisTemplate;
  private final AuthRateLimitProperties properties;

  public RedisAuthRateLimiter(
      StringRedisTemplate redisTemplate,
      AuthRateLimitProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  @Override
  public boolean allowExchange(String key) {
    return allow(
        AuthConstants.RateLimit.EXCHANGE_KEY_PREFIX + key,
        properties.getExchangeLimit(),
        properties.getExchangeWindow().toMillis());
  }

  @Override
  public boolean allowRename(String key) {
    return allow(
        AuthConstants.RateLimit.RENAME_KEY_PREFIX + key,
        properties.getRenameLimit(),
        properties.getRenameWindow().toMillis());
  }

  private boolean allow(String key, int limit, long windowMs) {
    Long current = redisTemplate.opsForValue().increment(key);
    if (current == null) {
      return false;
    }

    if (current == 1L) {
      redisTemplate.expire(key, windowMs, TimeUnit.MILLISECONDS);
    }
    return current <= limit;
  }
}
