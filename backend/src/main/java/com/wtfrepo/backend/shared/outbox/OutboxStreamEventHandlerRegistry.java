package com.wtfrepo.backend.shared.outbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Registry for mapping outbox event type to its domain handlers.
 *
 * <p>One event type can fan out to multiple handlers (for example Arena and Economy both consume
 * {@code SpecimenDeactivatedEvent}). Handler execution order follows bean discovery order.
 */
@Component
public class OutboxStreamEventHandlerRegistry {

  private final Map<String, List<OutboxStreamEventHandler>> handlersByEventType;
  private final int handlerCount;

  public OutboxStreamEventHandlerRegistry(List<OutboxStreamEventHandler> handlers) {
    Map<String, List<OutboxStreamEventHandler>> mapping = new LinkedHashMap<>();
    for (OutboxStreamEventHandler handler : handlers) {
      String eventType = normalizeEventType(handler.eventType());
      mapping.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(handler);
    }
    Map<String, List<OutboxStreamEventHandler>> immutableMapping = new LinkedHashMap<>();
    mapping.forEach((eventType, eventHandlers) -> immutableMapping.put(eventType, List.copyOf(eventHandlers)));
    this.handlersByEventType = Map.copyOf(immutableMapping);
    this.handlerCount = handlers.size();
  }

  public List<OutboxStreamEventHandler> findAll(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return List.of();
    }
    return handlersByEventType.getOrDefault(eventType.trim(), List.of());
  }

  public Optional<OutboxStreamEventHandler> find(String eventType) {
    return findAll(eventType).stream().findFirst();
  }

  public int handlerCount() {
    return handlerCount;
  }

  public int eventTypeCount() {
    return handlersByEventType.size();
  }

  private String normalizeEventType(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalStateException("Outbox stream handler eventType must not be blank");
    }
    return eventType.trim();
  }
}
