package com.wtfrepo.backend.economy.infra.persistence.entity;

import com.wtfrepo.backend.economy.domain.GameValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Immutable game session fact table.
 *
 * <p>该表记录每次提交分数的最终判定结果（VALID/REJECTED），用于审计与反刷追踪。
 */
@Getter
@Entity
@Table(
    name = "game_session",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_game_session_idempotency_key",
          columnNames = {"idempotency_key"})
    })
public class GameSessionJpaEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "game_type", nullable = false, length = 64)
  private String gameType;

  @Column(name = "score", nullable = false)
  private int score;

  @Column(name = "duration_ms", nullable = false)
  private int durationMs;

  @Column(name = "bug_earned", nullable = false)
  private int bugEarned;

  @Column(name = "balance_after", nullable = false)
  private long balanceAfter;

  @Column(name = "client_session_id", length = 128)
  private String clientSessionId;

  @Column(name = "extra_data", columnDefinition = "text")
  private String extraData;

  @Enumerated(EnumType.STRING)
  @Column(name = "validation_status", nullable = false, length = 32)
  private GameValidationStatus validationStatus;

  @Column(name = "validation_reason", length = 160)
  private String validationReason;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected GameSessionJpaEntity() {}

  public static GameSessionJpaEntity create(
      String userId,
      String gameType,
      int score,
      int durationMs,
      int bugEarned,
      long balanceAfter,
      String clientSessionId,
      String extraData,
      GameValidationStatus validationStatus,
      String validationReason,
      String idempotencyKey) {
    GameSessionJpaEntity entity = new GameSessionJpaEntity();
    entity.id = UUID.randomUUID().toString().replace("-", "");
    entity.userId = userId;
    entity.gameType = gameType;
    entity.score = score;
    entity.durationMs = durationMs;
    entity.bugEarned = bugEarned;
    entity.balanceAfter = balanceAfter;
    entity.clientSessionId = clientSessionId;
    entity.extraData = extraData;
    entity.validationStatus = validationStatus;
    entity.validationReason = validationReason;
    entity.idempotencyKey = idempotencyKey;
    entity.createdAt = Instant.now();
    return entity;
  }
}

