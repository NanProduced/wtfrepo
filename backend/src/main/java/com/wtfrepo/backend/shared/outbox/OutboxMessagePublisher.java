package com.wtfrepo.backend.shared.outbox;

/** Publishes outbox rows to external MQ transport. */
public interface OutboxMessagePublisher {

  /** Publishes one outbox event to downstream transport. */
  void publish(OutboxEventJpaEntity event);
}
