package com.wtfrepo.backend.economy.infra.persistence.entity;

import com.wtfrepo.backend.economy.domain.BetDirection;
import com.wtfrepo.backend.economy.domain.BetPoolStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;

/** Daily three-way parimutuel pool with house liquidity positions. */
@Getter
@Entity
@Table(name = "bet_pool")
public class BetPoolJpaEntity {

  @EmbeddedId private BetPoolId id;

  @Column(name = "pool_up", nullable = false)
  private long poolUp;

  @Column(name = "pool_flat", nullable = false)
  private long poolFlat;

  @Column(name = "pool_down", nullable = false)
  private long poolDown;

  @Column(name = "house_up", nullable = false)
  private long houseUp;

  @Column(name = "house_flat", nullable = false)
  private long houseFlat;

  @Column(name = "house_down", nullable = false)
  private long houseDown;

  @Column(name = "rake_rate", nullable = false, precision = 8, scale = 4)
  private BigDecimal rakeRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private BetPoolStatus status;

  @Column(name = "bet_cutoff_at", nullable = false)
  private Instant betCutoffAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected BetPoolJpaEntity() {}

  public static BetPoolJpaEntity createOpen(
      String specimenId,
      LocalDate tradingDay,
      long houseUp,
      long houseFlat,
      long houseDown,
      BigDecimal rakeRate,
      Instant betCutoffAt) {
    BetPoolJpaEntity entity = new BetPoolJpaEntity();
    entity.id = new BetPoolId(specimenId, tradingDay);
    entity.poolUp = 0L;
    entity.poolFlat = 0L;
    entity.poolDown = 0L;
    entity.houseUp = houseUp;
    entity.houseFlat = houseFlat;
    entity.houseDown = houseDown;
    entity.rakeRate = rakeRate;
    entity.status = BetPoolStatus.OPEN;
    entity.betCutoffAt = betCutoffAt;
    entity.createdAt = Instant.now();
    return entity;
  }

  public String getSpecimenId() {
    return id.getSpecimenId();
  }

  public LocalDate getDate() {
    return id.getDate();
  }

  public void addToPool(BetDirection direction, long amount) {
    if (direction == BetDirection.UP) {
      this.poolUp += amount;
      return;
    }
    if (direction == BetDirection.FLAT) {
      this.poolFlat += amount;
      return;
    }
    this.poolDown += amount;
  }

  public long totalPoolWithHouse() {
    return poolUp + poolFlat + poolDown + houseUp + houseFlat + houseDown;
  }

  public long directionTotalWithHouse(BetDirection direction) {
    if (direction == BetDirection.UP) {
      return poolUp + houseUp;
    }
    if (direction == BetDirection.FLAT) {
      return poolFlat + houseFlat;
    }
    return poolDown + houseDown;
  }
}
