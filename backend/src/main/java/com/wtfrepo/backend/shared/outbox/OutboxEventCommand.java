package com.wtfrepo.backend.shared.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable append command for outbox write path.
 *
 * <p>Business services should build this command and persist it in the same transaction together
 * with domain state updates.
 */
public record OutboxEventCommand(
    String aggregateType,
    String aggregateId,
    String eventType,
    String eventKey,
    Object payload,
    Instant occurredAt) {

  public OutboxEventCommand {
    aggregateType = requireText(aggregateType, "aggregateType");
    aggregateId = requireText(aggregateId, "aggregateId");
    eventType = requireText(eventType, "eventType");
    eventKey = requireText(eventKey, "eventKey");
    Objects.requireNonNull(payload, "payload must not be null");
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
