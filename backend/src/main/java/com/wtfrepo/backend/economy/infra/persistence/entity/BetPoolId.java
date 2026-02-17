package com.wtfrepo.backend.economy.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite primary key for daily betting pool. */
@Embeddable
public class BetPoolId implements Serializable {

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  protected BetPoolId() {}

  public BetPoolId(String specimenId, LocalDate date) {
    this.specimenId = specimenId;
    this.date = date;
  }

  public String getSpecimenId() {
    return specimenId;
  }

  public LocalDate getDate() {
    return date;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BetPoolId that)) {
      return false;
    }
    return Objects.equals(specimenId, that.specimenId) && Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(specimenId, date);
  }
}
