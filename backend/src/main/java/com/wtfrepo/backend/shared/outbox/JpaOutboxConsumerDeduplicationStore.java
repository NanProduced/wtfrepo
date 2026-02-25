package com.wtfrepo.backend.shared.outbox;

import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed consumer dedupe store for stream idempotency.
 *
 * <p>This implementation intentionally has no in-memory fallback. It is enabled only when the
 * consumer switch is on.
 */
@Component
@ConditionalOnBean(OutboxConsumerEventJpaRepository.class)
@ConditionalOnProperty(
    prefix = "app.shared.outbox",
    name = {"consumer-enabled", "consumer-dedupe-enabled"},
    havingValue = "true")
public class JpaOutboxConsumerDeduplicationStore implements OutboxConsumerDeduplicationStore {

  private final OutboxConsumerEventJpaRepository repository;
  private final OutboxRelayProperties properties;

  public JpaOutboxConsumerDeduplicationStore(
      OutboxConsumerEventJpaRepository repository, OutboxRelayProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Override
  @Transactional
  public boolean isProcessed(String consumerGroup, String eventId) {
    cleanupExpiredRows();
    return repository.existsByConsumerGroupAndEventId(consumerGroup, eventId);
  }

  @Override
  @Transactional
  public void markProcessed(
      String consumerGroup,
      String eventId,
      String eventType,
      String eventKey,
      String streamRecordId) {
    cleanupExpiredRows();
    OutboxConsumerEventJpaEntity entity =
        OutboxConsumerEventJpaEntity.of(
            consumerGroup, eventId, eventType, eventKey, streamRecordId, Instant.now());

    try {
      repository.save(entity);
    } catch (DataIntegrityViolationException ex) {
      if (repository.existsByConsumerGroupAndEventId(consumerGroup, eventId)) {
        return;
      }
      throw ex;
    }
  }

  private void cleanupExpiredRows() {
    if (Objects.isNull(properties.getConsumerDedupeRetention())
        || properties.getConsumerDedupeRetention().isNegative()
        || properties.getConsumerDedupeRetention().isZero()) {
      return;
    }

    repository.deleteByProcessedAtBefore(Instant.now().minus(properties.getConsumerDedupeRetention()));
  }
}
