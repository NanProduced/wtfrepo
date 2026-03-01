package com.wtfrepo.backend.economy.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/** Per-specimen house allocation overrides for betting pools. */
@Getter
@Entity
@Table(name = "bet_house_config")
public class BetHouseConfigJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "house_budget", nullable = false)
  private long houseBudget;

  @Column(name = "weight_up", nullable = false, precision = 8, scale = 4)
  private BigDecimal weightUp;

  @Column(name = "weight_flat", nullable = false, precision = 8, scale = 4)
  private BigDecimal weightFlat;

  @Column(name = "weight_down", nullable = false, precision = 8, scale = 4)
  private BigDecimal weightDown;

  @Column(name = "updated_by", nullable = false, length = 64)
  private String updatedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BetHouseConfigJpaEntity() {}

  public static BetHouseConfigJpaEntity create(
      String specimenId,
      long houseBudget,
      BigDecimal weightUp,
      BigDecimal weightFlat,
      BigDecimal weightDown,
      String updatedBy) {
    BetHouseConfigJpaEntity entity = new BetHouseConfigJpaEntity();
    entity.specimenId = specimenId;
    entity.houseBudget = houseBudget;
    entity.weightUp = weightUp;
    entity.weightFlat = weightFlat;
    entity.weightDown = weightDown;
    entity.updatedBy = updatedBy;
    Instant now = Instant.now();
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void update(
      long houseBudget,
      BigDecimal weightUp,
      BigDecimal weightFlat,
      BigDecimal weightDown,
      String updatedBy) {
    this.houseBudget = houseBudget;
    this.weightUp = weightUp;
    this.weightFlat = weightFlat;
    this.weightDown = weightDown;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }
}
