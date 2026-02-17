package com.wtfrepo.backend.arena.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;

/**
 * Daily Elo snapshot consumed by settlement and trend visualization.
 *
 * <p>Rows are keyed by {@code (specimen_id, date)} to support idempotent recomputation.
 */
@Getter
@Entity
@IdClass(EloDailySnapshotId.class)
@Table(name = "elo_daily_snapshot")
public class EloDailySnapshotJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Id
  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "elo_open", nullable = false)
  private int eloOpen;

  @Column(name = "elo_close", nullable = false)
  private int eloClose;

  @Column(name = "delta_r", nullable = false)
  private int deltaR;

  @Column(name = "matches_count", nullable = false)
  private int matchesCount;

  @Column(name = "both_bad_count", nullable = false)
  private int bothBadCount;

  @Column(name = "global_correction", nullable = false)
  private int globalCorrection;

  protected EloDailySnapshotJpaEntity() {}

  public static EloDailySnapshotJpaEntity create(
      String specimenId,
      LocalDate date,
      int eloOpen,
      int eloClose,
      int deltaR,
      int matchesCount,
      int bothBadCount) {
    EloDailySnapshotJpaEntity entity = new EloDailySnapshotJpaEntity();
    entity.specimenId = specimenId;
    entity.date = date;
    entity.refresh(eloOpen, eloClose, deltaR, matchesCount, bothBadCount);
    entity.globalCorrection = 0;
    return entity;
  }

  public void refresh(int eloOpen, int eloClose, int deltaR, int matchesCount, int bothBadCount) {
    this.eloOpen = eloOpen;
    this.eloClose = eloClose;
    this.deltaR = deltaR;
    this.matchesCount = matchesCount;
    this.bothBadCount = bothBadCount;
  }

  public void setGlobalCorrection(int globalCorrection) {
    this.globalCorrection = globalCorrection;
  }
}
