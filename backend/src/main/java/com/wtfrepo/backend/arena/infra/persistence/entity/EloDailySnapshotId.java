package com.wtfrepo.backend.arena.infra.persistence.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite key for {@link EloDailySnapshotJpaEntity}. */
public class EloDailySnapshotId implements Serializable {

  private String specimenId;
  private LocalDate date;

  protected EloDailySnapshotId() {}

  public EloDailySnapshotId(String specimenId, LocalDate date) {
    this.specimenId = specimenId;
    this.date = date;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof EloDailySnapshotId that)) {
      return false;
    }
    return Objects.equals(specimenId, that.specimenId) && Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(specimenId, date);
  }
}
