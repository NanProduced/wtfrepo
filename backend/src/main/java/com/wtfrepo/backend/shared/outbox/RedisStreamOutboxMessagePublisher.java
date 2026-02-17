package com.wtfrepo.backend.shared.outbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis Stream publisher for outbox events. */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "app.shared.outbox", name = {"relay-enabled", "jpa-enabled"}, havingValue = "true")
public class RedisStreamOutboxMessagePublisher implements OutboxMessagePublisher {

  private final StringRedisTemplate stringRedisTemplate;
  private final OutboxRelayProperties properties;

  public RedisStreamOutboxMessagePublisher(
      StringRedisTemplate stringRedisTemplate, OutboxRelayProperties properties) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.properties = properties;
  }

  @Override
  public void publish(OutboxEventJpaEntity event) {
    Map<String, String> message = new LinkedHashMap<>();
    message.put("eventId", event.getEventId());
    message.put("aggregateType", event.getAggregateType());
    message.put("aggregateId", event.getAggregateId());
    message.put("eventType", event.getEventType());
    message.put("eventKey", event.getEventKey());
    message.put("payload", event.getPayloadJson());
    message.put("occurredAt", event.getOccurredAt().toString());
    message.put("createdAt", event.getCreatedAt().toString());
    message.put("publishedAt", Instant.now().toString());

    RecordId recordId =
        stringRedisTemplate
            .opsForStream()
            .add(StreamRecords.newRecord().in(properties.getRedisStreamKey()).ofMap(message));
    if (recordId == null) {
      throw new IllegalStateException("Redis stream append returned null record id");
    }
  }
}
