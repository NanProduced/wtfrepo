package com.wtfrepo.backend.shared.outbox;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relay service that flushes pending outbox rows to MQ transport. */
@Service
@ConditionalOnBean({OutboxEventJpaRepository.class, OutboxMessagePublisher.class})
@ConditionalOnProperty(prefix = "app.shared.outbox", name = {"relay-enabled", "jpa-enabled"}, havingValue = "true")
public class OutboxRelayService {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final OutboxMessagePublisher outboxMessagePublisher;
  private final OutboxRelayProperties properties;

  public OutboxRelayService(
      OutboxEventJpaRepository outboxEventJpaRepository,
      OutboxMessagePublisher outboxMessagePublisher,
      OutboxRelayProperties properties) {
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.outboxMessagePublisher = outboxMessagePublisher;
    this.properties = properties;
  }

  @Transactional
  public RelayBatchResult relayPendingBatch() {
    List<OutboxEventJpaEntity> pendingEvents =
        outboxEventJpaRepository.findByStatusForUpdate(
            OutboxEventStatus.PENDING, PageRequest.of(0, properties.getRelayBatchSize()));

    int publishedCount = 0;
    int failedCount = 0;
    for (OutboxEventJpaEntity event : pendingEvents) {
      try {
        outboxMessagePublisher.publish(event);
        event.markPublished(Instant.now());
        publishedCount++;
      } catch (RuntimeException ex) {
        event.recordPublishFailure(ex.getMessage(), properties.getRelayMaxAttempts());
        failedCount++;
        log.warn(
            "outbox_relay_publish_failed eventId={} eventType={} attempts={} reason={}",
            event.getEventId(),
            event.getEventType(),
            event.getAttemptCount(),
            safeError(ex));
      }
    }

    if (!pendingEvents.isEmpty()) {
      log.info(
          "outbox_relay_batch_done scanned={} published={} failed={}",
          pendingEvents.size(),
          publishedCount,
          failedCount);
    }
    return new RelayBatchResult(pendingEvents.size(), publishedCount, failedCount);
  }

  private String safeError(RuntimeException ex) {
    if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
      return "unknown";
    }
    return ex.getMessage();
  }

  public record RelayBatchResult(int scannedCount, int publishedCount, int failedCount) {}
}
