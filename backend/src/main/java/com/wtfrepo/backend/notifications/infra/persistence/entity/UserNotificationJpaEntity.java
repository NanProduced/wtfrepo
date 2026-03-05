package com.wtfrepo.backend.notifications.infra.persistence.entity;

import com.wtfrepo.backend.notifications.domain.NotificationStatus;
import com.wtfrepo.backend.notifications.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Notification inbox persistence model.
 *
 * <p>Flyway initialization is intentionally deferred in this project stage. JPA entities are used
 * first so contracts can be stabilized before schema freeze.
 */
@Getter
@Entity
@Table(
    name = "user_notification",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_notification_uid",
          columnNames = {"notification_uid"}),
      @UniqueConstraint(
          name = "uk_notification_dedupe",
          columnNames = {"receiver_user_id", "type", "dedupe_key"})
    })
public class UserNotificationJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "notification_uid", nullable = false, length = 64)
  private String notificationUid;

  @Column(name = "receiver_user_id", nullable = false, length = 64)
  private String receiverUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 40)
  private NotificationType type;

  @Column(name = "dedupe_key", nullable = false, length = 200)
  private String dedupeKey;

  @Column(name = "title", nullable = false, length = 120)
  private String title;

  @Column(name = "body", columnDefinition = "text")
  private String body;

  @Column(name = "aggregate_count", nullable = false)
  private int aggregateCount;

  @Column(name = "actor_user_id", length = 64)
  private String actorUserId;

  @Column(name = "actor_nickname", length = 60)
  private String actorNickname;

  @Column(name = "target_url", length = 500)
  private String targetUrl;

  @Column(name = "fallback_url", length = 500)
  private String fallbackUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private NotificationStatus status;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserNotificationJpaEntity() {}

  public static UserNotificationJpaEntity create(
      String receiverUserId,
      NotificationType type,
      String dedupeKey,
      String title,
      String body,
      int aggregateCount,
      String actorUserId,
      String actorNickname,
      String targetUrl,
      String fallbackUrl) {
    Instant now = Instant.now();
    UserNotificationJpaEntity entity = new UserNotificationJpaEntity();
    entity.notificationUid = UUID.randomUUID().toString();
    entity.receiverUserId = receiverUserId;
    entity.type = type;
    entity.dedupeKey = dedupeKey;
    entity.title = title;
    entity.body = body;
    entity.aggregateCount = aggregateCount;
    entity.actorUserId = actorUserId;
    entity.actorNickname = actorNickname;
    entity.targetUrl = targetUrl;
    entity.fallbackUrl = fallbackUrl;
    entity.status = NotificationStatus.UNREAD;
    entity.readAt = null;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public boolean markRead() {
    if (this.status == NotificationStatus.READ) {
      return false;
    }
    this.status = NotificationStatus.READ;
    this.readAt = Instant.now();
    this.updatedAt = this.readAt;
    return true;
  }

  public void applyAggregation(int aggregateCount, String body) {
    this.aggregateCount = aggregateCount;
    this.body = body;
    this.updatedAt = Instant.now();
  }
}
