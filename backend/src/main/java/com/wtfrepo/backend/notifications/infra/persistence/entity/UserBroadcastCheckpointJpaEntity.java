package com.wtfrepo.backend.notifications.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Broadcast backfill checkpoint per user.
 *
 * <p>Flyway initialization is intentionally deferred in this project stage. JPA entities are used
 * first so contracts can be stabilized before schema freeze.
 */
@Getter
@Entity
@Table(name = "user_broadcast_checkpoint")
public class UserBroadcastCheckpointJpaEntity {

  @Id
  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "last_broadcast_at")
  private Instant lastBroadcastAt;

  @Column(name = "last_broadcast_id")
  private Long lastBroadcastId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserBroadcastCheckpointJpaEntity() {}

  public static UserBroadcastCheckpointJpaEntity create(
      String userId, Instant lastBroadcastAt, Long lastBroadcastId) {
    Instant now = Instant.now();
    UserBroadcastCheckpointJpaEntity entity = new UserBroadcastCheckpointJpaEntity();
    entity.userId = userId;
    entity.lastBroadcastAt = lastBroadcastAt;
    entity.lastBroadcastId = lastBroadcastId;
    entity.updatedAt = now;
    return entity;
  }

  public void advanceTo(Instant lastBroadcastAt, Long lastBroadcastId) {
    this.lastBroadcastAt = lastBroadcastAt;
    this.lastBroadcastId = lastBroadcastId;
    this.updatedAt = Instant.now();
  }
}
