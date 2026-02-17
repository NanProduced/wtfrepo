package com.wtfrepo.backend.shared.outbox;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for consumer-side dedupe rows. */
public interface OutboxConsumerEventJpaRepository
    extends JpaRepository<OutboxConsumerEventJpaEntity, Long> {

  boolean existsByConsumerGroupAndEventId(String consumerGroup, String eventId);

  void deleteByProcessedAtBefore(Instant threshold);
}
