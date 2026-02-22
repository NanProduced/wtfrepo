package com.wtfrepo.backend.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisStreamOutboxConsumerServiceTest {

  @Mock
  private StringRedisTemplate stringRedisTemplate;

  @Mock
  private StreamOperations<String, Object, Object> streamOperations;

  @Mock
  private OutboxStreamEventHandler eventHandler;

  @Mock
  private OutboxConsumerDeduplicationStore deduplicationStore;

  private OutboxRelayProperties properties;

  @BeforeEach
  void setUp() {
    properties = new OutboxRelayProperties();
    properties.setRedisStreamKey("wtfrepo:outbox:events");
    properties.setConsumerGroup("wtfrepo:outbox:consumer");
    properties.setConsumerName("consumer-1");
    properties.setConsumerBatchSize(20);
    properties.setConsumerBlockMs(100);
    when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
  }

  @Test
  void shouldDispatchAndAckWhenHandlerExists() {
    when(eventHandler.eventType()).thenReturn("VoteCompletedEvent");
    OutboxStreamEventHandlerRegistry registry =
        new OutboxStreamEventHandlerRegistry(List.of(eventHandler));

    MapRecord<String, Object, Object> record = mockRecord("VoteCompletedEvent", "evt-1", "171000-0");
    stubReadBatch(List.of(record));

    RedisStreamOutboxConsumerService service =
        new RedisStreamOutboxConsumerService(
            stringRedisTemplate, properties, registry, Optional.of(deduplicationStore));

    when(deduplicationStore.isProcessed(properties.getConsumerGroup(), "evt-1")).thenReturn(false);

    RedisStreamOutboxConsumerService.ConsumerBatchResult result = service.pollAndDispatch();

    verify(eventHandler).handle(any(OutboxStreamMessage.class));
    verify(deduplicationStore)
        .markProcessed(
            properties.getConsumerGroup(),
            "evt-1",
            "VoteCompletedEvent",
            "arena:key",
            "171000-0");
    verify(streamOperations)
        .acknowledge(
            properties.getRedisStreamKey(), properties.getConsumerGroup(), record.getId().getValue());
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.consumedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(0);
    assertThat(result.unknownCount()).isEqualTo(0);
    assertThat(result.ackedCount()).isEqualTo(1);
  }

  @Test
  void shouldKeepUnknownEventPendingByDefault() {
    OutboxStreamEventHandlerRegistry registry = new OutboxStreamEventHandlerRegistry(List.of());
    MapRecord<String, Object, Object> record = mockRecord("IpoCompletedEvent", "evt-2", "171001-0");
    stubReadBatch(List.of(record));

    RedisStreamOutboxConsumerService service =
        new RedisStreamOutboxConsumerService(
            stringRedisTemplate, properties, registry, Optional.of(deduplicationStore));

    when(deduplicationStore.isProcessed(properties.getConsumerGroup(), "evt-2")).thenReturn(false);

    RedisStreamOutboxConsumerService.ConsumerBatchResult result = service.pollAndDispatch();

    verify(streamOperations, never())
        .acknowledge(
            properties.getRedisStreamKey(), properties.getConsumerGroup(), record.getId().getValue());
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.consumedCount()).isEqualTo(0);
    assertThat(result.failedCount()).isEqualTo(0);
    assertThat(result.unknownCount()).isEqualTo(1);
    assertThat(result.ackedCount()).isEqualTo(0);
  }

  @Test
  void shouldAckUnknownEventWhenConfigured() {
    properties.setConsumerAckUnknownEventType(true);
    OutboxStreamEventHandlerRegistry registry = new OutboxStreamEventHandlerRegistry(List.of());
    MapRecord<String, Object, Object> record =
        mockRecord("DailySnapshotCreatedEvent", "evt-3", "171002-0");
    stubReadBatch(List.of(record));

    RedisStreamOutboxConsumerService service =
        new RedisStreamOutboxConsumerService(
            stringRedisTemplate, properties, registry, Optional.of(deduplicationStore));

    when(deduplicationStore.isProcessed(properties.getConsumerGroup(), "evt-3")).thenReturn(false);

    RedisStreamOutboxConsumerService.ConsumerBatchResult result = service.pollAndDispatch();

    verify(streamOperations)
        .acknowledge(
            properties.getRedisStreamKey(), properties.getConsumerGroup(), record.getId().getValue());
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.consumedCount()).isEqualTo(0);
    assertThat(result.failedCount()).isEqualTo(0);
    assertThat(result.unknownCount()).isEqualTo(1);
    assertThat(result.ackedCount()).isEqualTo(1);
  }

  @Test
  void shouldSkipHandlerAndAckWhenMessageAlreadyProcessed() {
    when(eventHandler.eventType()).thenReturn("VoteCompletedEvent");
    OutboxStreamEventHandlerRegistry registry =
        new OutboxStreamEventHandlerRegistry(List.of(eventHandler));
    MapRecord<String, Object, Object> record = mockRecord("VoteCompletedEvent", "evt-4", "171003-0");
    stubReadBatch(List.of(record));

    when(deduplicationStore.isProcessed(properties.getConsumerGroup(), "evt-4")).thenReturn(true);

    RedisStreamOutboxConsumerService service =
        new RedisStreamOutboxConsumerService(
            stringRedisTemplate, properties, registry, Optional.of(deduplicationStore));

    RedisStreamOutboxConsumerService.ConsumerBatchResult result = service.pollAndDispatch();

    verify(eventHandler, never()).handle(any(OutboxStreamMessage.class));
    verify(deduplicationStore, never())
        .markProcessed(any(), any(), any(), any(), any());
    verify(streamOperations)
        .acknowledge(
            properties.getRedisStreamKey(), properties.getConsumerGroup(), record.getId().getValue());
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.consumedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(0);
    assertThat(result.unknownCount()).isEqualTo(0);
    assertThat(result.ackedCount()).isEqualTo(1);
  }

  @Test
  void shouldDispatchAllHandlersForSameEventType() {
    OutboxStreamEventHandler secondaryHandler = mock(OutboxStreamEventHandler.class);
    when(eventHandler.eventType()).thenReturn("SpecimenDeactivatedEvent");
    when(secondaryHandler.eventType()).thenReturn("SpecimenDeactivatedEvent");
    OutboxStreamEventHandlerRegistry registry =
        new OutboxStreamEventHandlerRegistry(List.of(eventHandler, secondaryHandler));
    MapRecord<String, Object, Object> record =
        mockRecord("SpecimenDeactivatedEvent", "evt-5", "171004-0");
    stubReadBatch(List.of(record));

    when(deduplicationStore.isProcessed(properties.getConsumerGroup(), "evt-5")).thenReturn(false);

    RedisStreamOutboxConsumerService service =
        new RedisStreamOutboxConsumerService(
            stringRedisTemplate, properties, registry, Optional.of(deduplicationStore));

    RedisStreamOutboxConsumerService.ConsumerBatchResult result = service.pollAndDispatch();

    verify(eventHandler, times(1)).handle(any(OutboxStreamMessage.class));
    verify(secondaryHandler, times(1)).handle(any(OutboxStreamMessage.class));
    verify(streamOperations)
        .acknowledge(
            properties.getRedisStreamKey(), properties.getConsumerGroup(), record.getId().getValue());
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.consumedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(0);
    assertThat(result.unknownCount()).isEqualTo(0);
    assertThat(result.ackedCount()).isEqualTo(1);
  }

  private void stubReadBatch(List<MapRecord<String, Object, Object>> records) {
    doReturn("OK")
        .when(streamOperations)
        .createGroup(
            eq(properties.getRedisStreamKey()), any(ReadOffset.class), eq(properties.getConsumerGroup()));
    doReturn(records)
        .when(streamOperations)
        .read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));
  }

  private MapRecord<String, Object, Object> mockRecord(
      String eventType, String eventId, String recordId) {
    @SuppressWarnings("unchecked")
    MapRecord<String, Object, Object> record = mock(MapRecord.class);
    when(record.getId()).thenReturn(RecordId.of(recordId));
    when(record.getStream()).thenReturn(properties.getRedisStreamKey());
    when(record.getValue())
        .thenReturn(
            Map.of(
                "eventId", eventId,
                "aggregateType", "ARENA_BATTLE",
                "aggregateId", "battle-1",
                "eventType", eventType,
                "eventKey", "arena:key",
                "payload", "{}",
                "occurredAt", "2026-02-15T00:00:00Z",
                "createdAt", "2026-02-15T00:00:01Z",
                "publishedAt", "2026-02-15T00:00:02Z"));
    return record;
  }
}
