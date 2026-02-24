package com.wtfrepo.backend.comments.infra.support;

import com.wtfrepo.backend.comments.application.CommentAuthorRoleResolver;
import com.wtfrepo.backend.comments.application.support.CommentsIdentityBindingCacheProperties;
import com.wtfrepo.backend.comments.domain.CommentAuthorRepoRoleSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Redis-cached resolver for comment author repo roles (TTL 5m). */
@Primary
@Component
public class CachedCommentAuthorRoleResolver implements CommentAuthorRoleResolver {

  private static final Logger log = LoggerFactory.getLogger(CachedCommentAuthorRoleResolver.class);

  private final CommentAuthorRoleResolver delegate;
  private final StringRedisTemplate redisTemplate;
  private final CommentsIdentityBindingCacheProperties properties;

  public CachedCommentAuthorRoleResolver(
      @Qualifier("directCommentAuthorRoleResolver") CommentAuthorRoleResolver delegate,
      StringRedisTemplate redisTemplate,
      CommentsIdentityBindingCacheProperties properties) {
    this.delegate = delegate;
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  @Override
  public CommentAuthorRepoRoleSnapshot resolveRole(String specimenId, String userId) {
    if (!StringUtils.hasText(specimenId) || !StringUtils.hasText(userId)) {
      return CommentAuthorRepoRoleSnapshot.NONE;
    }
    String key = cacheKey(specimenId.trim(), userId.trim());
    CommentAuthorRepoRoleSnapshot cached = loadCached(key);
    if (cached != null) {
      return cached;
    }
    CommentAuthorRepoRoleSnapshot resolved = delegate.resolveRole(specimenId, userId);
    if (resolved == null) {
      resolved = CommentAuthorRepoRoleSnapshot.NONE;
    }
    storeCached(key, resolved);
    return resolved;
  }

  private String cacheKey(String specimenId, String userId) {
    return properties.getCacheKeyPrefix() + ":" + specimenId + ":" + userId;
  }

  private CommentAuthorRepoRoleSnapshot loadCached(String key) {
    try {
      String value = redisTemplate.opsForValue().get(key);
      if (!StringUtils.hasText(value)) {
        return null;
      }
      return CommentAuthorRepoRoleSnapshot.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn("comments_identity_cache_value_invalid key={}", key);
      redisTemplate.delete(key);
      return null;
    } catch (RuntimeException ex) {
      log.warn("comments_identity_cache_read_failed key={}", key, ex);
      return null;
    }
  }

  private void storeCached(String key, CommentAuthorRepoRoleSnapshot resolved) {
    try {
      redisTemplate.opsForValue().set(key, resolved.name(), properties.getTtl());
    } catch (RuntimeException ex) {
      log.warn("comments_identity_cache_write_failed key={}", key, ex);
    }
  }
}
