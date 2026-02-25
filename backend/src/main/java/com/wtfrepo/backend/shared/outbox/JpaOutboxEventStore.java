package com.wtfrepo.backend.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed outbox writer.
 *
 * <p>By default this writer is disabled until table DDL is finalized.
 */
@Component
@Primary
@ConditionalOnBean(OutboxEventJpaRepository.class)
public class JpaOutboxEventStore implements OutboxEventStore {

  private static final Logger log = LoggerFactory.getLogger(JpaOutboxEventStore.class);

  private final OutboxEventJpaRepository repository;
  private final ObjectMapper objectMapper;

  public JpaOutboxEventStore(OutboxEventJpaRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void append(OutboxEventCommand command) {
    OutboxEventJpaEntity entity =
        OutboxEventJpaEntity.createPending(
            command.aggregateType(),
            command.aggregateId(),
            command.eventType(),
            command.eventKey(),
            toJson(command.payload()),
            command.occurredAt());
    try {
      repository.save(entity);
    } catch (DataIntegrityViolationException ex) {
      // Duplicate event keys can happen on safe retries; treat them as idempotent success.
      if (repository.existsByEventKey(command.eventKey())) {
        log.debug(
            "outbox_append_idempotent_skip eventType={} eventKey={}",
            command.eventType(),
            command.eventKey());
        return;
      }
      throw ex;
    }
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize outbox payload", ex);
    }
  }
}
