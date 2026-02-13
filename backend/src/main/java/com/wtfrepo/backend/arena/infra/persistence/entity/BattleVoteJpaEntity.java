package com.wtfrepo.backend.arena.infra.persistence.entity;

import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
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
 * Vote facts for a single arena battle.
 *
 * <p>Schema migration is intentionally deferred until MVP review.
 */
@Getter
@Entity
@Table(
    name = "battle_vote",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_battle_vote_idempotency_key",
          columnNames = {"idempotency_key"}),
      @UniqueConstraint(
          name = "uk_battle_vote_battle_voter",
          columnNames = {"battle_id", "voter_id"})
    })
public class BattleVoteJpaEntity {

  @Id
  @Column(name = "vote_id", nullable = false, length = 64)
  private String voteId;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false, length = 256)
  private String requestFingerprint;

  @Column(name = "battle_id", nullable = false, length = 160)
  private String battleId;

  @Column(name = "left_specimen_id", nullable = false, length = 64)
  private String leftSpecimenId;

  @Column(name = "right_specimen_id", nullable = false, length = 64)
  private String rightSpecimenId;

  @Enumerated(EnumType.STRING)
  @Column(name = "winner", nullable = false, length = 16)
  private ArenaVoteWinner winner;

  @Column(name = "voter_id", nullable = false, length = 64)
  private String voterId;

  @Column(name = "bug_cost", nullable = false)
  private int bugCost;

  @Column(name = "left_elo_before", nullable = false)
  private int leftEloBefore;

  @Column(name = "left_elo_delta", nullable = false)
  private int leftEloDelta;

  @Column(name = "right_elo_before", nullable = false)
  private int rightEloBefore;

  @Column(name = "right_elo_delta", nullable = false)
  private int rightEloDelta;

  @Column(name = "left_elo_after", nullable = false)
  private int leftEloAfter;

  @Column(name = "right_elo_after", nullable = false)
  private int rightEloAfter;

  @Column(name = "left_k_factor", nullable = false)
  private int leftKFactor;

  @Column(name = "right_k_factor", nullable = false)
  private int rightKFactor;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false, length = 32)
  private ArenaMatchType matchType;

  @Column(name = "match_profile_version", nullable = false, length = 64)
  private String matchProfileVersion;

  @Column(name = "left_phase", nullable = false, length = 32)
  private String leftPhase;

  @Column(name = "right_phase", nullable = false, length = 32)
  private String rightPhase;

  @Column(name = "wallet_balance_after", nullable = false)
  private long walletBalanceAfter;

  @Column(name = "policy_snapshot", nullable = false, columnDefinition = "text")
  private String policySnapshot;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected BattleVoteJpaEntity() {}

  public static BattleVoteJpaEntity of(
      String idempotencyKey,
      String requestFingerprint,
      String battleId,
      String leftSpecimenId,
      String rightSpecimenId,
      ArenaVoteWinner winner,
      String voterId,
      int bugCost,
      int leftEloBefore,
      int leftEloDelta,
      int rightEloBefore,
      int rightEloDelta,
      int leftEloAfter,
      int rightEloAfter,
      int leftKFactor,
      int rightKFactor,
      ArenaMatchType matchType,
      String matchProfileVersion,
      String leftPhase,
      String rightPhase,
      long walletBalanceAfter,
      String policySnapshotJson) {
    BattleVoteJpaEntity entity = new BattleVoteJpaEntity();
    entity.voteId = UUID.randomUUID().toString().replace("-", "");
    entity.idempotencyKey = idempotencyKey;
    entity.requestFingerprint = requestFingerprint;
    entity.battleId = battleId;
    entity.leftSpecimenId = leftSpecimenId;
    entity.rightSpecimenId = rightSpecimenId;
    entity.winner = winner;
    entity.voterId = voterId;
    entity.bugCost = bugCost;
    entity.leftEloBefore = leftEloBefore;
    entity.leftEloDelta = leftEloDelta;
    entity.rightEloBefore = rightEloBefore;
    entity.rightEloDelta = rightEloDelta;
    entity.leftEloAfter = leftEloAfter;
    entity.rightEloAfter = rightEloAfter;
    entity.leftKFactor = leftKFactor;
    entity.rightKFactor = rightKFactor;
    entity.matchType = matchType;
    entity.matchProfileVersion = matchProfileVersion;
    entity.leftPhase = leftPhase;
    entity.rightPhase = rightPhase;
    entity.walletBalanceAfter = walletBalanceAfter;
    entity.policySnapshot = policySnapshotJson;
    entity.createdAt = Instant.now();
    return entity;
  }

  public void updateWalletBalanceAfter(long walletBalanceAfter) {
    this.walletBalanceAfter = walletBalanceAfter;
  }
}
