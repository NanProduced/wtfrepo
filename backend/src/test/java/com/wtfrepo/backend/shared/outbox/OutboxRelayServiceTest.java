package com.wtfrepo.backend.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

  @Mock
  private OutboxEventJpaRepository outboxEventJpaRepository;

  @Mock
  private OutboxMessagePublisher outboxMessagePublisher;

  private OutboxRelayProperties properties;
  private OutboxRelayService outboxRelayService;

  @BeforeEach
  void setUp() {
    properties = new OutboxRelayProperties();
    properties.setRelayBatchSize(20);
    properties.setRelayMaxAttempts(3);
    outboxRelayService =
        new OutboxRelayService(outboxEventJpaRepository, outboxMessagePublisher, properties);
  }

  @Test
  void shouldMarkEventPublishedWhenRedisPublishSucceeds() {
    OutboxEventJpaEntity event =
        OutboxEventJpaEntity.createPending(
            "ARENA_BATTLE",
            "bat_1",
            "VoteCompletedEvent",
            "arena:vote-completed:key-1",
            "{\"winner\":\"LEFT\"}",
            Instant.parse("2026-02-15T00:00:00Z"));
    when(outboxEventJpaRepository.findByStatusForUpdate(any(), any())).thenReturn(List.of(event));

    OutboxRelayService.RelayBatchResult result = outboxRelayService.relayPendingBatch();

    verify(outboxMessagePublisher).publish(event);
    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.publishedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(0);
    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(event.getPublishedAt()).isNotNull();
    assertThat(event.getAttemptCount()).isEqualTo(0);
  }

  @Test
  void shouldMarkEventFailedWhenPublishKeepsFailing() {
    properties.setRelayMaxAttempts(1);
    OutboxEventJpaEntity event =
        OutboxEventJpaEntity.createPending(
            "ARENA_SPECIMEN",
            "spm_1",
            "DailySnapshotCreatedEvent",
            "arena:daily-snapshot:spm_1:2026-02-15",
            "{\"deltaR\":12}",
            Instant.parse("2026-02-15T00:00:00Z"));
    when(outboxEventJpaRepository.findByStatusForUpdate(any(), any())).thenReturn(List.of(event));
    doThrow(new IllegalStateException("redis unavailable")).when(outboxMessagePublisher).publish(event);

    OutboxRelayService.RelayBatchResult result = outboxRelayService.relayPendingBatch();

    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.publishedCount()).isEqualTo(0);
    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(event.getAttemptCount()).isEqualTo(1);
    assertThat(event.getLastError()).contains("redis unavailable");
  }
}
