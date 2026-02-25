package com.wtfrepo.backend.achievements.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/** Inbox persistence for achievement-triggering outbox events. */
@Getter
@Entity
@Table(name = "achievement_inbox_event")
public class AchievementInboxEventJpaEntity {

  @Id
  @Column(name = "event_id", nullable = false, length = 64)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "event_key", length = 192)
  private String eventKey;

  @Column(name = "aggregate_type", length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", length = 128)
  private String aggregateId;

  @Column(name = "payload_json", nullable = false, columnDefinition = "text")
  private String payloadJson;

  @Column(name = "occurred_at")
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected AchievementInboxEventJpaEntity() {}

  public static AchievementInboxEventJpaEntity of(
      String eventId,
      String eventType,
      String eventKey,
      String aggregateType,
      String aggregateId,
      String payloadJson,
      Instant occurredAt) {
    AchievementInboxEventJpaEntity entity = new AchievementInboxEventJpaEntity();
    entity.eventId = eventId;
    entity.eventType = eventType;
    entity.eventKey = eventKey;
    entity.aggregateType = aggregateType;
    entity.aggregateId = aggregateId;
    entity.payloadJson = payloadJson;
    entity.occurredAt = occurredAt;
    entity.receivedAt = Instant.now();
    return entity;
  }
}
