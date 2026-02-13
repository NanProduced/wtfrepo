package com.wtfrepo.backend.arena.infra.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.arena.application.ArenaDuelService;
import com.wtfrepo.backend.arena.application.ArenaFeaturedDuelStore;
import com.wtfrepo.backend.arena.application.support.ArenaFeaturedDuelProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redis-backed featured duel cache for anonymous traffic.
 *
 * <p>This allows anonymous `GET /arena/duel` requests to avoid re-running the full matching engine
 * on every request while preserving contract semantics.
 */
@Component
public class RedisArenaFeaturedDuelStore implements ArenaFeaturedDuelStore {

  private static final Logger log = LoggerFactory.getLogger(RedisArenaFeaturedDuelStore.class);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final ArenaFeaturedDuelProperties properties;

  public RedisArenaFeaturedDuelStore(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      ArenaFeaturedDuelProperties properties) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public Optional<ArenaDuelService.DuelResult> findFeaturedDuel() {
    String key = properties.getCacheKey();
    try {
      String payload = redisTemplate.opsForValue().get(key);
      if (!StringUtils.hasText(payload)) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(payload, ArenaDuelService.DuelResult.class));
    } catch (JsonProcessingException ex) {
      log.warn("arena_featured_duel_payload_invalid key={}", key, ex);
      redisTemplate.delete(key);
      return Optional.empty();
    } catch (RuntimeException ex) {
      log.warn("arena_featured_duel_read_failed key={}", key, ex);
      return Optional.empty();
    }
  }

  @Override
  public void saveFeaturedDuel(ArenaDuelService.DuelResult duelResult) {
    String key = properties.getCacheKey();
    try {
      String payload = objectMapper.writeValueAsString(duelResult);
      redisTemplate.opsForValue().set(key, payload, properties.getCacheTtl());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize featured duel payload", ex);
    } catch (RuntimeException ex) {
      log.warn("arena_featured_duel_write_failed key={}", key, ex);
    }
  }
}

