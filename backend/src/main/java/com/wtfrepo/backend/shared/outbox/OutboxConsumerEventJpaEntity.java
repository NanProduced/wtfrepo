package com.wtfrepo.backend.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * Consumer-side deduplication row for Redis Stream messages.
 *
 * <p>DDL is postponed until full MVP entity review is completed.
 */
@Getter
@Entity
@Table(
    name = "outbox_consumer_event",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_outbox_consumer_event_group_event",
          columnNames = {"consumer_group", "event_id"})
    },
    indexes = {
      @Index(name = "idx_outbox_consumer_event_processed", columnList = "processed_at")
    })
public class OutboxConsumerEventJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "consumer_group", nullable = false, length = 128)
  private String consumerGroup;

  @Column(name = "event_id", nullable = false, length = 64)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "event_key", nullable = false, length = 192)
  private String eventKey;

  @Column(name = "stream_record_id", nullable = false, length = 64)
  private String streamRecordId;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected OutboxConsumerEventJpaEntity() {}

  public static OutboxConsumerEventJpaEntity of(
      String consumerGroup,
      String eventId,
      String eventType,
      String eventKey,
      String streamRecordId,
      Instant processedAt) {
    OutboxConsumerEventJpaEntity entity = new OutboxConsumerEventJpaEntity();
    entity.consumerGroup = consumerGroup;
    entity.eventId = eventId;
    entity.eventType = eventType;
    entity.eventKey = eventKey;
    entity.streamRecordId = streamRecordId;
    entity.processedAt = processedAt == null ? Instant.now() : processedAt;
    return entity;
  }
}
