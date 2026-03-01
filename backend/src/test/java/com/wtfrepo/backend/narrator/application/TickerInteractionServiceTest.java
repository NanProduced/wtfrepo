package com.wtfrepo.backend.narrator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.wtfrepo.backend.narrator.application.NarratorOutboxEventPublisher.TickerItemClickedPayload;
import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TickerInteractionServiceTest {

  @Mock
  private OutboxEventStore outboxEventStore;

  private TickerInteractionService service;

  @BeforeEach
  void setUp() {
    NarratorOutboxEventPublisher publisher = new NarratorOutboxEventPublisher(outboxEventStore);
    service = new TickerInteractionService(publisher);
  }

  @Test
  void recordTickerItemClickShouldPublishOutboxEvent() {
    service.recordTickerItemClick(
        "u_1",
        new NarratorModels.TickerItemClickCommand(
            "evt_001", "ticker.elo_delta_major", "/specimen/sp_123"),
        "idem_click_1");

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand command = captor.getValue();
    assertThat(command.aggregateType()).isEqualTo("NARRATOR_TICKER");
    assertThat(command.aggregateId()).isEqualTo("u_1");
    assertThat(command.eventType()).isEqualTo("TickerItemClickedEvent");
    assertThat(command.eventKey()).startsWith("narrator:ticker-click:u_1:");

    assertThat(command.payload()).isInstanceOf(TickerItemClickedPayload.class);
    TickerItemClickedPayload payload = (TickerItemClickedPayload) command.payload();
    assertThat(payload.userId()).isEqualTo("u_1");
    assertThat(payload.eventId()).isEqualTo("evt_001");
    assertThat(payload.eventType()).isEqualTo("ticker.elo_delta_major");
    assertThat(payload.actionUrl()).isEqualTo("/specimen/sp_123");
  }

  @Test
  void recordTickerItemClickShouldRejectInvalidPayload() {
    assertThatThrownBy(
            () ->
                service.recordTickerItemClick(
                    "u_1",
                    new NarratorModels.TickerItemClickCommand(null, "ticker.elo_delta_major", null),
                    "idem_click_2"))
        .hasMessage("invalid_payload");
  }

  @Test
  void recordTickerItemClickShouldRejectMissingIdempotencyKey() {
    assertThatThrownBy(
            () ->
                service.recordTickerItemClick(
                    "u_1",
                    new NarratorModels.TickerItemClickCommand("evt_001", "ticker.rank_surge", null),
                    "   "))
        .hasMessage("invalid_idempotency_key");
  }
}
