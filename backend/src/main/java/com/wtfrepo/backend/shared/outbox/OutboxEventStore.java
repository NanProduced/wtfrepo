package com.wtfrepo.backend.shared.outbox;

/** Write port for appending domain events into outbox storage. */
public interface OutboxEventStore {

  /** Appends an outbox event atomically with caller transaction. */
  void append(OutboxEventCommand command);
}
