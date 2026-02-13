package com.wtfrepo.backend.economy.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/** Daily claim fact table used for idempotent claim execution. */
@Getter
@Entity
@Table(
    name = "economy_daily_claim",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_economy_daily_claim_user_day",
          columnNames = {"user_id", "trading_day"})
    })
public class EconomyDailyClaimJpaEntity {

  @Id
  @Column(name = "claim_id", nullable = false, length = 64)
  private String claimId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "trading_day", nullable = false)
  private LocalDate tradingDay;

  @Column(name = "amount", nullable = false)
  private int amount;

  @Column(name = "balance_after", nullable = false)
  private long balanceAfter;

  @Column(name = "policy_version", nullable = false, length = 128)
  private String policyVersion;

  @Column(name = "policy_source", nullable = false, length = 64)
  private String policySource;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EconomyDailyClaimJpaEntity() {}

  public static EconomyDailyClaimJpaEntity create(
      String userId,
      LocalDate tradingDay,
      int amount,
      long balanceAfter,
      String policyVersion,
      String policySource) {
    EconomyDailyClaimJpaEntity entity = new EconomyDailyClaimJpaEntity();
    entity.claimId = UUID.randomUUID().toString().replace("-", "");
    entity.userId = userId;
    entity.tradingDay = tradingDay;
    entity.amount = amount;
    entity.balanceAfter = balanceAfter;
    entity.policyVersion = policyVersion;
    entity.policySource = policySource;
    entity.createdAt = Instant.now();
    return entity;
  }
}

