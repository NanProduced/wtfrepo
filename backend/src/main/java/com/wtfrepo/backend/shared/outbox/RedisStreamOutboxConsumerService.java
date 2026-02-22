package com.wtfrepo.backend.shared.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Stream consumer-group poller for shared outbox events.
 *
 * <p>This service provides only transport and dispatch skeleton. Domain modules decide which event
 * types to handle by registering {@link OutboxStreamEventHandler} beans.
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "app.shared.outbox", name = "consumer-enabled", havingValue = "true")
public class RedisStreamOutboxConsumerService {

  private static final Logger log = LoggerFactory.getLogger(RedisStreamOutboxConsumerService.class);

  private final StringRedisTemplate stringRedisTemplate;
  private final OutboxRelayProperties properties;
  private final OutboxStreamEventHandlerRegistry handlerRegistry;
  private final Optional<OutboxConsumerDeduplicationStore> deduplicationStore;

  private volatile boolean consumerGroupReady;

  public RedisStreamOutboxConsumerService(
      StringRedisTemplate stringRedisTemplate,
      OutboxRelayProperties properties,
      OutboxStreamEventHandlerRegistry handlerRegistry,
      Optional<OutboxConsumerDeduplicationStore> deduplicationStore) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.properties = properties;
    this.handlerRegistry = handlerRegistry;
    this.deduplicationStore = deduplicationStore;
    this.consumerGroupReady = false;
  }

  /** Polls one batch from Redis Stream and dispatches to domain handlers. */
  public ConsumerBatchResult pollAndDispatch() {
    ensureConsumerGroupReady();
    if (!consumerGroupReady) {
      return ConsumerBatchResult.empty();
    }

    List<MapRecord<String, String, String>> records = readNextBatch();
    if (records == null || records.isEmpty()) {
      return ConsumerBatchResult.empty();
    }

    int consumedCount = 0;
    int failedCount = 0;
    int unknownCount = 0;
    int ackedCount = 0;

    for (MapRecord<String, String, String> record : records) {
      OutboxStreamMessage message = OutboxStreamMessage.fromRecord(record);

      if (!message.isStructurallyValid()) {
        failedCount++;
        log.warn(
            "outbox_stream_invalid_message eventType={} eventId={} recordId={}",
            message.eventType(),
            message.eventId(),
            message.streamRecordId());
        continue;
      }

      if (isAlreadyProcessed(message)) {
        acknowledge(record);
        consumedCount++;
        ackedCount++;
        log.debug(
            "outbox_stream_duplicate_skip eventType={} eventId={} recordId={}",
            message.eventType(),
            message.eventId(),
            message.streamRecordId());
        continue;
      }

      List<OutboxStreamEventHandler> handlers = handlerRegistry.findAll(message.eventType());

      if (handlers.isEmpty()) {
        unknownCount++;
        log.warn(
            "outbox_stream_no_handler eventType={} eventId={} recordId={}",
            message.eventType(),
            message.eventId(),
            message.streamRecordId());
        if (properties.isConsumerAckUnknownEventType()) {
          acknowledge(record);
          ackedCount++;
        }
        continue;
      }

      try {
        // Fan-out dispatch: all handlers for the same eventType run before ack.
        // On any handler failure we keep the message pending for retry (handlers must be idempotent).
        for (OutboxStreamEventHandler handler : handlers) {
          handler.handle(message);
        }
        markProcessed(message);
        acknowledge(record);
        consumedCount++;
        ackedCount++;
      } catch (RuntimeException ex) {
        failedCount++;
        log.warn(
            "outbox_stream_consume_failed eventType={} eventId={} recordId={} reason={}",
            message.eventType(),
            message.eventId(),
            message.streamRecordId(),
            safeError(ex));
      }
    }

    return new ConsumerBatchResult(records.size(), consumedCount, failedCount, unknownCount, ackedCount);
  }

  @SuppressWarnings("unchecked")
  private List<MapRecord<String, String, String>> readNextBatch() {
    try {
      return (List<MapRecord<String, String, String>>)
          (List<?>)
              stringRedisTemplate
                  .opsForStream()
                  .read(
                      Consumer.from(properties.getConsumerGroup(), properties.getConsumerName()),
                      StreamReadOptions.empty()
                          .count(Math.max(1, properties.getConsumerBatchSize()))
                          .block(Duration.ofMillis(Math.max(1L, properties.getConsumerBlockMs()))),
                      StreamOffset.create(properties.getRedisStreamKey(), ReadOffset.lastConsumed()));
    } catch (RuntimeException ex) {
      if (isNoGroup(ex)) {
        consumerGroupReady = false;
        log.warn(
            "outbox_stream_group_missing stream={} group={} reason={}",
            properties.getRedisStreamKey(),
            properties.getConsumerGroup(),
            safeError(ex));
        return List.of();
      }
      throw ex;
    }
  }

  private void ensureConsumerGroupReady() {
    if (consumerGroupReady) {
      return;
    }

    try {
      stringRedisTemplate
          .opsForStream()
          .createGroup(
              properties.getRedisStreamKey(), ReadOffset.lastConsumed(), properties.getConsumerGroup());
      consumerGroupReady = true;
      log.info(
          "outbox_stream_group_created stream={} group={} handlers={}",
          properties.getRedisStreamKey(),
          properties.getConsumerGroup(),
          handlerRegistry.handlerCount());
    } catch (RuntimeException ex) {
      if (isBusyGroup(ex)) {
        consumerGroupReady = true;
        return;
      }
      if (isMissingStream(ex)) {
        log.debug(
            "outbox_stream_group_create_deferred stream={} group={} reason={}",
            properties.getRedisStreamKey(),
            properties.getConsumerGroup(),
            safeError(ex));
        return;
      }

      log.warn(
          "outbox_stream_group_create_failed stream={} group={} reason={}",
          properties.getRedisStreamKey(),
          properties.getConsumerGroup(),
          safeError(ex));
    }
  }

  private boolean isAlreadyProcessed(OutboxStreamMessage message) {
    return deduplicationStore
        .filter(ignored -> properties.isConsumerDedupeEnabled())
        .map(store -> store.isProcessed(properties.getConsumerGroup(), message.eventId()))
        .orElse(false);
  }

  private void markProcessed(OutboxStreamMessage message) {
    deduplicationStore
        .filter(ignored -> properties.isConsumerDedupeEnabled())
        .ifPresent(
            store ->
                store.markProcessed(
                    properties.getConsumerGroup(),
                    message.eventId(),
                    message.eventType(),
                    message.eventKey(),
                    message.streamRecordId()));
  }

  private void acknowledge(MapRecord<String, String, String> record) {
    stringRedisTemplate
        .opsForStream()
        .acknowledge(
            properties.getRedisStreamKey(), properties.getConsumerGroup(), record.getId().getValue());
  }

  private boolean isBusyGroup(RuntimeException ex) {
    return containsMessage(ex, "BUSYGROUP");
  }

  private boolean isMissingStream(RuntimeException ex) {
    return containsMessage(ex, "requires the key to exist")
        || containsMessage(ex, "no such key")
        || containsMessage(ex, "NOKEY");
  }

  private boolean isNoGroup(RuntimeException ex) {
    return containsMessage(ex, "NOGROUP");
  }

  private boolean containsMessage(RuntimeException ex, String keyword) {
    if (ex == null || keyword == null || keyword.isBlank()) {
      return false;
    }
    String message = safeError(ex);
    return message.toUpperCase().contains(keyword.toUpperCase());
  }

  private String safeError(RuntimeException ex) {
    if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
      return "unknown";
    }
    return ex.getMessage();
  }

  public record ConsumerBatchResult(
      int scannedCount, int consumedCount, int failedCount, int unknownCount, int ackedCount) {

    private static ConsumerBatchResult empty() {
      return new ConsumerBatchResult(0, 0, 0, 0, 0);
    }
  }
}
