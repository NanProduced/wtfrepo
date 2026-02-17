package com.wtfrepo.backend.shared.outbox;

/**
 * Persistence contract for consumer-side idempotency in Redis Stream processing.
 *
 * <p>Implementations should guarantee that one `(consumerGroup, eventId)` is recorded once.
 */
public interface OutboxConsumerDeduplicationStore {

  /** Returns whether a message is already processed for the given consumer group. */
  boolean isProcessed(String consumerGroup, String eventId);

  /** Marks a message as processed after successful domain side effects. */
  void markProcessed(
      String consumerGroup,
      String eventId,
      String eventType,
      String eventKey,
      String streamRecordId);
}
