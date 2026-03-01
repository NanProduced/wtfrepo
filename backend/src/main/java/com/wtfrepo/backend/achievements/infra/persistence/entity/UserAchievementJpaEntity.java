package com.wtfrepo.backend.achievements.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** User achievement unlock record with one-row-per-achievement semantics. */
@Getter
@Entity
@Table(
    name = "user_achievement",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_achievement_user_code",
          columnNames = {"user_id", "achievement_code"})
    },
    indexes = {
      @Index(name = "idx_user_achievement_user", columnList = "user_id"),
      @Index(name = "idx_user_achievement_code", columnList = "achievement_code")
    })
public class UserAchievementJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "achievement_code", nullable = false, length = 64)
  private String achievementCode;

  @Column(name = "unlock_source_event_id", length = 64)
  private String unlockSourceEventId;

  @Column(name = "unlock_context", columnDefinition = "text")
  private String unlockContext;

  @Column(name = "reward_bug", nullable = false)
  private int rewardBug;

  @Column(name = "unlocked_at", nullable = false)
  private Instant unlockedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserAchievementJpaEntity() {}

  public static UserAchievementJpaEntity create(
      String userId,
      String achievementCode,
      String unlockSourceEventId,
      String unlockContext,
      int rewardBug,
      Instant unlockedAt) {
    Instant now = unlockedAt == null ? Instant.now() : unlockedAt;
    UserAchievementJpaEntity entity = new UserAchievementJpaEntity();
    entity.id = "ua_" + UUID.randomUUID().toString().replace("-", "");
    entity.userId = userId;
    entity.achievementCode = achievementCode;
    entity.unlockSourceEventId = unlockSourceEventId;
    entity.unlockContext = unlockContext;
    entity.rewardBug = Math.max(0, rewardBug);
    entity.unlockedAt = now;
    entity.createdAt = now;
    return entity;
  }
}
