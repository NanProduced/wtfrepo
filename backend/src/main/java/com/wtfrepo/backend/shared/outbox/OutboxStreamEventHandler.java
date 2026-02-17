package com.wtfrepo.backend.shared.outbox;

/**
 * Domain-facing consumer contract for Redis Stream outbox messages.
 *
 * <p>Each handler owns exactly one event type and must implement idempotent side effects.
 */
public interface OutboxStreamEventHandler {

  /** Event type handled by this consumer, for example {@code VoteCompletedEvent}. */
  String eventType();

  /**
   * Handles one outbox message.
   *
   * <p>Throwing an exception keeps message unacked so it can be retried.
   */
  void handle(OutboxStreamMessage message);
}
