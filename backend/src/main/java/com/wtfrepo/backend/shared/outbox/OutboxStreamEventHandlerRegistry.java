package com.wtfrepo.backend.shared.outbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Registry for mapping outbox event type to its domain handler.
 *
 * <p>Duplicate event type bindings are rejected at startup to avoid nondeterministic consumption.
 */
@Component
public class OutboxStreamEventHandlerRegistry {

  private final Map<String, OutboxStreamEventHandler> handlersByEventType;

  public OutboxStreamEventHandlerRegistry(List<OutboxStreamEventHandler> handlers) {
    Map<String, OutboxStreamEventHandler> mapping = new LinkedHashMap<>();
    for (OutboxStreamEventHandler handler : handlers) {
      String eventType = normalizeEventType(handler.eventType());
      OutboxStreamEventHandler existing = mapping.putIfAbsent(eventType, handler);
      if (existing != null) {
        throw new IllegalStateException("Duplicate outbox stream handler for eventType=" + eventType);
      }
    }
    this.handlersByEventType = Map.copyOf(mapping);
  }

  public Optional<OutboxStreamEventHandler> find(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(handlersByEventType.get(eventType.trim()));
  }

  public int handlerCount() {
    return handlersByEventType.size();
  }

  private String normalizeEventType(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalStateException("Outbox stream handler eventType must not be blank");
    }
    return eventType.trim();
  }
}
