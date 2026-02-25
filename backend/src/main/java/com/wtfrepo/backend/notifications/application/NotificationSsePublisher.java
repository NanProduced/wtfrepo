package com.wtfrepo.backend.notifications.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wtfrepo.backend.notifications.infra.persistence.entity.UserNotificationJpaEntity;
import com.wtfrepo.backend.shared.json.JsonUtils;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Publishes notification SSE payloads to Redis pub/sub channels. */
@Service
public class NotificationSsePublisher {

  private static final Logger log = LoggerFactory.getLogger(NotificationSsePublisher.class);
  private static final String PAGER_CHANNEL_PREFIX = "pager:";

  private final StringRedisTemplate redisTemplate;
  private final JsonUtils jsonUtils;

  public NotificationSsePublisher(StringRedisTemplate redisTemplate, JsonUtils jsonUtils) {
    this.redisTemplate = redisTemplate;
    this.jsonUtils = jsonUtils;
  }

  public void publishPager(UserNotificationJpaEntity entity) {
    if (entity == null) {
      return;
    }
    String userId = entity.getReceiverUserId();
    if (!StringUtils.hasText(userId)) {
      return;
    }
    PagerPayload payload =
        new PagerPayload(
            "pager",
            entity.getType() != null ? entity.getType().name() : null,
            entity.getNotificationUid(),
            entity.getTitle(),
            entity.getBody(),
            entity.getTargetUrl(),
            entity.getActorNickname(),
            entity.getAggregateCount(),
            entity.getCreatedAt());
    publish(PAGER_CHANNEL_PREFIX + userId.trim(), payload);
  }

  private void publish(String channel, Object payload) {
    if (!StringUtils.hasText(channel) || payload == null) {
      return;
    }
    String json;
    try {
      json = jsonUtils.toJson(payload);
    } catch (JsonProcessingException ex) {
      log.warn("notify_sse_publish_skip channel={} reason=serialize_failed", channel, ex);
      return;
    }

    try {
      redisTemplate.convertAndSend(channel, json);
    } catch (RuntimeException ex) {
      log.warn("notify_sse_publish_failed channel={}", channel, ex);
    }
  }

  private record PagerPayload(
      String channel,
      String type,
      String notificationUid,
      String title,
      String body,
      String targetUrl,
      String actorNickname,
      int aggregateCount,
      Instant createdAt) {}
}
