package com.wtfrepo.backend.arena.infra.support;

import com.wtfrepo.backend.arena.application.ArenaRateLimiter;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Redis-backed arena rate limiter. */
@Component
public class RedisArenaRateLimiter implements ArenaRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisArenaRateLimiter.class);

  private final StringRedisTemplate redisTemplate;
  private final ArenaRateLimitProperties properties;

  public RedisArenaRateLimiter(
      StringRedisTemplate redisTemplate, ArenaRateLimitProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  @Override
  public boolean allowAnonymousDuel(String clientIp) {
    return allow(
        ArenaConstants.RateLimit.DUEL_ANON_KEY_PREFIX,
        normalizeKey(clientIp),
        properties.getDuelAnonymousLimit(),
        properties.getDuelAnonymousWindow());
  }

  @Override
  public boolean allowAuthenticatedDuelByUser(String userId) {
    return allow(
        ArenaConstants.RateLimit.DUEL_AUTH_USER_KEY_PREFIX,
        normalizeKey(userId),
        properties.getDuelAuthenticatedUserLimit(),
        properties.getDuelAuthenticatedUserWindow());
  }

  @Override
  public boolean allowAuthenticatedDuelByIp(String clientIp) {
    return allow(
        ArenaConstants.RateLimit.DUEL_AUTH_IP_KEY_PREFIX,
        normalizeKey(clientIp),
        properties.getDuelAuthenticatedIpLimit(),
        properties.getDuelAuthenticatedIpWindow());
  }

  private boolean allow(String keyPrefix, String keySuffix, int limit, java.time.Duration window) {
    String redisKey = keyPrefix + keySuffix;
    try {
      Long current = redisTemplate.opsForValue().increment(redisKey);
      if (current == null) {
        return false;
      }
      if (current == 1L) {
        redisTemplate.expire(redisKey, window);
      }
      return current <= limit;
    } catch (RuntimeException ex) {
      // Keep API available when Redis is transiently unavailable; monitoring should catch this.
      log.warn("arena_rate_limit_fallback_allow key={}", redisKey, ex);
      return true;
    }
  }

  private String normalizeKey(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "unknown";
    }
    return raw.trim();
  }
}

