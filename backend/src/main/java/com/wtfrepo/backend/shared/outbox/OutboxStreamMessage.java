package com.wtfrepo.backend.shared.outbox;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.connection.stream.MapRecord;

/**
 * Canonical envelope decoded from Redis Stream outbox records.
 *
 * <p>Domain modules consume this abstraction so shared MQ infrastructure remains transport-focused.
 */
public record OutboxStreamMessage(
    String streamRecordId,
    String streamKey,
    String eventId,
    String aggregateType,
    String aggregateId,
    String eventType,
    String eventKey,
    String payload,
    Instant occurredAt,
    Instant createdAt,
    Instant publishedAt,
    Map<String, String> rawFields) {

  /** Builds a typed envelope from one Redis Stream record. */
  public static OutboxStreamMessage fromRecord(MapRecord<String, String, String> record) {
    Map<String, String> raw = new LinkedHashMap<>(record.getValue());
    return new OutboxStreamMessage(
        record.getId().getValue(),
        record.getStream(),
        raw.get("eventId"),
        raw.get("aggregateType"),
        raw.get("aggregateId"),
        raw.get("eventType"),
        raw.get("eventKey"),
        raw.get("payload"),
        parseInstant(raw.get("occurredAt")),
        parseInstant(raw.get("createdAt")),
        parseInstant(raw.get("publishedAt")),
        Map.copyOf(raw));
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  /** True when message has a non-empty event type and payload key fields. */
  public boolean isStructurallyValid() {
    return isNonBlank(eventType) && isNonBlank(eventId) && Objects.nonNull(rawFields);
  }

  private boolean isNonBlank(String value) {
    return value != null && !value.isBlank();
  }
}
