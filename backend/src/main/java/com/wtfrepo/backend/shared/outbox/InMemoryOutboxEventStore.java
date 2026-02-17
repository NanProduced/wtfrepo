package com.wtfrepo.backend.shared.outbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-memory fallback for environments without JPA outbox repository.
 *
 * <p>This keeps service wiring stable in unit tests and lightweight local runs before DB schema is
 * finalized.
 */
@Component
@ConditionalOnMissingBean(OutboxEventStore.class)
public class InMemoryOutboxEventStore implements OutboxEventStore {

  private final Map<String, OutboxEventCommand> eventsByKey = new ConcurrentHashMap<>();

  @Override
  public void append(OutboxEventCommand command) {
    eventsByKey.putIfAbsent(command.eventKey(), command);
  }

  /** Returns current events ordered by occurrence time for assertions or diagnostics. */
  public List<OutboxEventCommand> snapshot() {
    List<OutboxEventCommand> events = new ArrayList<>(eventsByKey.values());
    events.sort(Comparator.comparing(OutboxEventCommand::occurredAt));
    return List.copyOf(events);
  }
}
