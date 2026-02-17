package com.wtfrepo.backend.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxStreamEventHandlerRegistryTest {

  @Test
  void shouldFindRegisteredHandlerByEventType() {
    OutboxStreamEventHandler handler = new StubHandler("VoteCompletedEvent");
    OutboxStreamEventHandlerRegistry registry = new OutboxStreamEventHandlerRegistry(List.of(handler));

    assertThat(registry.find("VoteCompletedEvent")).contains(handler);
    assertThat(registry.handlerCount()).isEqualTo(1);
  }

  @Test
  void shouldRejectDuplicateEventTypeBindings() {
    OutboxStreamEventHandler left = new StubHandler("DailySnapshotCreatedEvent");
    OutboxStreamEventHandler right = new StubHandler("DailySnapshotCreatedEvent");

    assertThatThrownBy(() -> new OutboxStreamEventHandlerRegistry(List.of(left, right)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate outbox stream handler");
  }

  private static final class StubHandler implements OutboxStreamEventHandler {

    private final String eventType;

    private StubHandler(String eventType) {
      this.eventType = eventType;
    }

    @Override
    public String eventType() {
      return eventType;
    }

    @Override
    public void handle(OutboxStreamMessage message) {
      // no-op for registry tests.
    }
  }
}
