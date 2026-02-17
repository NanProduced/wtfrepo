package com.wtfrepo.backend.economy.infra.persistence.entity;

import com.wtfrepo.backend.economy.domain.BetDirection;
import com.wtfrepo.backend.economy.domain.BetOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/** Immutable bet order fact with idempotency and settlement fields. */
@Getter
@Entity
@Table(
    name = "bet_order",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_bet_order_idempotency_key", columnNames = {"idempotency_key"})
    })
public class BetOrderJpaEntity {

  @Id
  @Column(name = "order_id", nullable = false, length = 64)
  private String orderId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 16)
  private BetDirection direction;

  @Column(name = "amount", nullable = false)
  private int amount;

  @Column(name = "odds_at_place", nullable = false, precision = 12, scale = 4)
  private BigDecimal oddsAtPlace;

  @Column(name = "settle_date", nullable = false)
  private LocalDate settleDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private BetOrderStatus status;

  @Column(name = "payout")
  private Long payout;

  @Column(name = "moon_doom_bonus")
  private Long moonDoomBonus;

  @Column(name = "settled_at")
  private Instant settledAt;

  @Column(name = "is_house", nullable = false)
  private boolean isHouse;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false, length = 256)
  private String requestFingerprint;

  @Column(name = "wallet_balance_after", nullable = false)
  private long walletBalanceAfter;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected BetOrderJpaEntity() {}

  public static BetOrderJpaEntity createUserOrder(
      String userId,
      String specimenId,
      BetDirection direction,
      int amount,
      BigDecimal oddsAtPlace,
      LocalDate settleDate,
      String idempotencyKey,
      String requestFingerprint,
      long walletBalanceAfter) {
    BetOrderJpaEntity entity = new BetOrderJpaEntity();
    entity.orderId = newOrderId();
    entity.userId = userId;
    entity.specimenId = specimenId;
    entity.direction = direction;
    entity.amount = amount;
    entity.oddsAtPlace = oddsAtPlace;
    entity.settleDate = settleDate;
    entity.status = BetOrderStatus.PENDING;
    entity.payout = null;
    entity.moonDoomBonus = null;
    entity.settledAt = null;
    entity.isHouse = false;
    entity.idempotencyKey = idempotencyKey;
    entity.requestFingerprint = requestFingerprint;
    entity.walletBalanceAfter = walletBalanceAfter;
    entity.createdAt = Instant.now();
    return entity;
  }

  public static BetOrderJpaEntity createHouseOrder(
      String specimenId,
      BetDirection direction,
      int amount,
      BigDecimal oddsAtPlace,
      LocalDate settleDate,
      String houseUserId,
      String idempotencyKey) {
    BetOrderJpaEntity entity = new BetOrderJpaEntity();
    entity.orderId = newOrderId();
    entity.userId = houseUserId;
    entity.specimenId = specimenId;
    entity.direction = direction;
    entity.amount = amount;
    entity.oddsAtPlace = oddsAtPlace;
    entity.settleDate = settleDate;
    entity.status = BetOrderStatus.PENDING;
    entity.payout = null;
    entity.moonDoomBonus = null;
    entity.settledAt = null;
    entity.isHouse = true;
    entity.idempotencyKey = idempotencyKey;
    entity.requestFingerprint = "house|" + specimenId + "|" + settleDate + "|" + direction;
    entity.walletBalanceAfter = 0L;
    entity.createdAt = Instant.now();
    return entity;
  }

  public boolean sameRequestFingerprint(String fingerprint) {
    return requestFingerprint.equals(fingerprint);
  }

  private static String newOrderId() {
    return "bet_ord_" + UUID.randomUUID().toString().replace("-", "");
  }
}
