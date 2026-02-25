package com.wtfrepo.backend.notifications.infra.persistence.entity;

import com.wtfrepo.backend.notifications.domain.BroadcastStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * System broadcast persistence model.
 *
 * <p>Flyway initialization is intentionally deferred in this project stage. JPA entities are used
 * first so contracts can be stabilized before schema freeze.
 */
@Getter
@Entity
@Table(name = "system_broadcast")
public class SystemBroadcastJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "broadcast_uid", nullable = false, length = 64)
  private String broadcastUid;

  @Column(name = "title", nullable = false, length = 120)
  private String title;

  @Column(name = "body", columnDefinition = "text")
  private String body;

  @Column(name = "target_url", length = 500)
  private String targetUrl;

  @Column(name = "total_recipients", nullable = false)
  private int totalRecipients;

  @Column(name = "delivered_count", nullable = false)
  private int deliveredCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BroadcastStatus status;

  @Column(name = "created_by", nullable = false, length = 64)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected SystemBroadcastJpaEntity() {}

  public static SystemBroadcastJpaEntity create(
      String createdBy, String title, String body, String targetUrl) {
    Instant now = Instant.now();
    SystemBroadcastJpaEntity entity = new SystemBroadcastJpaEntity();
    entity.broadcastUid = UUID.randomUUID().toString();
    entity.title = title;
    entity.body = body;
    entity.targetUrl = targetUrl;
    entity.totalRecipients = 0;
    entity.deliveredCount = 0;
    entity.status = BroadcastStatus.PENDING;
    entity.createdBy = createdBy;
    entity.createdAt = now;
    entity.completedAt = null;
    return entity;
  }

  public void markDelivering(int totalRecipients) {
    this.totalRecipients = totalRecipients;
    this.status = BroadcastStatus.DELIVERING;
    this.completedAt = null;
  }

  public void markCompleted(int deliveredCount) {
    this.deliveredCount = deliveredCount;
    this.status = BroadcastStatus.COMPLETED;
    this.completedAt = Instant.now();
  }

  public void markFailed(int deliveredCount) {
    this.deliveredCount = deliveredCount;
    this.status = BroadcastStatus.FAILED;
    this.completedAt = Instant.now();
  }
}
