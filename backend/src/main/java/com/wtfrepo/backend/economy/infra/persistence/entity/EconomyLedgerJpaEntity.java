package com.wtfrepo.backend.economy.infra.persistence.entity;

import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Immutable wallet ledger entries for deduction/claim auditability. */
@Getter
@Entity
@Table(
    name = "economy_ledger",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_economy_ledger_idempotency_key",
          columnNames = {"idempotency_key"})
    })
public class EconomyLedgerJpaEntity {

  @Id
  @Column(name = "ledger_id", nullable = false, length = 64)
  private String ledgerId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 32)
  private EconomyLedgerType entryType;

  @Column(name = "delta", nullable = false)
  private long delta;

  @Column(name = "balance_after", nullable = false)
  private long balanceAfter;

  @Column(name = "ref_type", nullable = false, length = 64)
  private String refType;

  @Column(name = "ref_id", nullable = false, length = 160)
  private String refId;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "bug_cost")
  private Integer bugCost;

  @Column(name = "min_bet")
  private Integer minBet;

  @Column(name = "rake_rate", precision = 8, scale = 4)
  private BigDecimal rakeRate;

  @Column(name = "policy_version", nullable = false, length = 128)
  private String policyVersion;

  @Column(name = "policy_source", nullable = false, length = 64)
  private String policySource;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EconomyLedgerJpaEntity() {}

  public static EconomyLedgerJpaEntity create(
      String userId,
      EconomyLedgerType entryType,
      long delta,
      long balanceAfter,
      String refType,
      String refId,
      String idempotencyKey,
      Integer bugCost,
      Integer minBet,
      BigDecimal rakeRate,
      String policyVersion,
      String policySource) {
    EconomyLedgerJpaEntity entity = new EconomyLedgerJpaEntity();
    entity.ledgerId = UUID.randomUUID().toString().replace("-", "");
    entity.userId = userId;
    entity.entryType = entryType;
    entity.delta = delta;
    entity.balanceAfter = balanceAfter;
    entity.refType = refType;
    entity.refId = refId;
    entity.idempotencyKey = idempotencyKey;
    entity.bugCost = bugCost;
    entity.minBet = minBet;
    entity.rakeRate = rakeRate;
    entity.policyVersion = policyVersion;
    entity.policySource = policySource;
    entity.createdAt = Instant.now();
    return entity;
  }
}

