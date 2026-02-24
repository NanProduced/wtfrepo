package com.wtfrepo.backend.shared.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Redis-backed token blacklist store for admin JWT revocation. */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisTokenBlacklistStore implements TokenBlacklistStore {

  private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistStore.class);

  private final StringRedisTemplate redisTemplate;
  private final SecurityTokenProperties tokenProperties;

  public RedisTokenBlacklistStore(
      StringRedisTemplate redisTemplate, SecurityTokenProperties tokenProperties) {
    this.redisTemplate = redisTemplate;
    this.tokenProperties = tokenProperties;
  }

  @Override
  public void blacklist(String tokenId, Duration ttl) {
    if (!StringUtils.hasText(tokenId) || ttl == null || ttl.isZero() || ttl.isNegative()) {
      return;
    }
    String key = keyFor(tokenId);
    try {
      redisTemplate.opsForValue().set(key, "1", ttl);
    } catch (RuntimeException ex) {
      log.warn("token_blacklist_write_failed key={}", key, ex);
    }
  }

  @Override
  public boolean isBlacklisted(String tokenId) {
    if (!StringUtils.hasText(tokenId)) {
      return false;
    }
    String key = keyFor(tokenId);
    try {
      Boolean exists = redisTemplate.hasKey(key);
      return Boolean.TRUE.equals(exists);
    } catch (RuntimeException ex) {
      log.warn("token_blacklist_read_failed key={}", key, ex);
      return false;
    }
  }

  private String keyFor(String tokenId) {
    return tokenProperties.getBlacklistKeyPrefix() + ":" + tokenId.trim();
  }
}
