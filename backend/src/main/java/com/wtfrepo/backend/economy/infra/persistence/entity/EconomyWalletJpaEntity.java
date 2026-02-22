package com.wtfrepo.backend.economy.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Economy wallet aggregate root.
 *
 * <p>At current MVP stage this table only stores mutable balance. Contract-facing
 * {@code totalEarned/totalSpent} are computed from {@code economy_ledger} in read path and will
 * be revisited when schema is frozen for initialization SQL.
 */
@Getter
@Entity
@Table(name = "economy_wallet")
public class EconomyWalletJpaEntity {

  @Id
  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "balance", nullable = false)
  private long balance;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected EconomyWalletJpaEntity() {}

  public static EconomyWalletJpaEntity create(String userId, long initialBalance) {
    Instant now = Instant.now();
    EconomyWalletJpaEntity entity = new EconomyWalletJpaEntity();
    entity.userId = userId;
    entity.balance = initialBalance;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public long credit(long amount) {
    this.balance += amount;
    this.updatedAt = Instant.now();
    return this.balance;
  }

  public long debit(long amount) {
    this.balance -= amount;
    this.updatedAt = Instant.now();
    return this.balance;
  }
}
