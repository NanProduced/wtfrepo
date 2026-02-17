package com.wtfrepo.backend.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Persistent outbox row used for reliable at-least-once event delivery.
 *
 * <p>DDL migration is intentionally postponed until all related entities are finalized.
 */
@Getter
@Entity
@Table(
    name = "outbox_event",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_outbox_event_key", columnNames = {"event_key"})
    },
    indexes = {
      @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
      @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
    })
public class OutboxEventJpaEntity {

  @Id
  @Column(name = "event_id", nullable = false, length = 64)
  private String eventId;

  @Column(name = "aggregate_type", nullable = false, length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 128)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "event_key", nullable = false, length = 192)
  private String eventKey;

  @Column(name = "payload_json", nullable = false, columnDefinition = "text")
  private String payloadJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private OutboxEventStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = 512)
  private String lastError;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxEventJpaEntity() {}

  public static OutboxEventJpaEntity createPending(
      String aggregateType,
      String aggregateId,
      String eventType,
      String eventKey,
      String payloadJson,
      Instant occurredAt) {
    OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
    entity.eventId = UUID.randomUUID().toString().replace("-", "");
    entity.aggregateType = aggregateType;
    entity.aggregateId = aggregateId;
    entity.eventType = eventType;
    entity.eventKey = eventKey;
    entity.payloadJson = payloadJson;
    entity.status = OutboxEventStatus.PENDING;
    entity.attemptCount = 0;
    entity.lastError = null;
    entity.occurredAt = occurredAt;
    entity.createdAt = Instant.now();
    entity.publishedAt = null;
    return entity;
  }

  public void markPublished(Instant publishedAt) {
    this.status = OutboxEventStatus.PUBLISHED;
    this.publishedAt = publishedAt == null ? Instant.now() : publishedAt;
    this.lastError = null;
  }

  /**
   * Records one publish failure.
   *
   * <p>The row remains {@link OutboxEventStatus#PENDING} for retry until max attempts is reached.
   */
  public void recordPublishFailure(String errorMessage, int maxAttempts) {
    this.attemptCount++;
    this.lastError = truncateError(errorMessage);
    this.status = this.attemptCount >= Math.max(1, maxAttempts) ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING;
  }

  private String truncateError(String errorMessage) {
    if (errorMessage == null) {
      return null;
    }
    if (errorMessage.length() <= 512) {
      return errorMessage;
    }
    return errorMessage.substring(0, 512);
  }
}
