package com.wtfrepo.backend.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JpaOutboxConsumerDeduplicationStoreTest {

  @Mock
  private OutboxConsumerEventJpaRepository repository;

  private OutboxRelayProperties properties;
  private JpaOutboxConsumerDeduplicationStore store;

  @BeforeEach
  void setUp() {
    properties = new OutboxRelayProperties();
    properties.setConsumerDedupeRetention(Duration.ofDays(7));
    store = new JpaOutboxConsumerDeduplicationStore(repository, properties);
  }

  @Test
  void shouldReturnProcessedWhenRepositoryContainsRecord() {
    when(repository.existsByConsumerGroupAndEventId("group-a", "evt-1")).thenReturn(true);

    boolean processed = store.isProcessed("group-a", "evt-1");

    assertThat(processed).isTrue();
    verify(repository).deleteByProcessedAtBefore(any());
  }

  @Test
  void shouldSaveProcessedRecord() {
    store.markProcessed("group-a", "evt-2", "VoteCompletedEvent", "arena:key", "171000-0");

    verify(repository).save(any(OutboxConsumerEventJpaEntity.class));
    verify(repository).deleteByProcessedAtBefore(any());
  }

  @Test
  void shouldIgnoreDuplicateConstraintViolationWhenRecordAlreadyExists() {
    when(repository.save(any(OutboxConsumerEventJpaEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));
    when(repository.existsByConsumerGroupAndEventId("group-a", "evt-3")).thenReturn(true);

    store.markProcessed("group-a", "evt-3", "DailySnapshotCreatedEvent", "arena:key", "171001-0");

    verify(repository).existsByConsumerGroupAndEventId("group-a", "evt-3");
  }

  @Test
  void shouldRethrowWhenConstraintViolationIsNotDuplicate() {
    when(repository.save(any(OutboxConsumerEventJpaEntity.class)))
        .thenThrow(new DataIntegrityViolationException("unknown integrity issue"));
    when(repository.existsByConsumerGroupAndEventId("group-a", "evt-4")).thenReturn(false);

    assertThatThrownBy(
            () ->
                store.markProcessed(
                    "group-a", "evt-4", "IpoCompletedEvent", "arena:key", "171002-0"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldSkipCleanupWhenRetentionDisabled() {
    properties.setConsumerDedupeRetention(Duration.ZERO);
    store.isProcessed("group-a", "evt-5");

    verify(repository, never()).deleteByProcessedAtBefore(any());
  }
}
