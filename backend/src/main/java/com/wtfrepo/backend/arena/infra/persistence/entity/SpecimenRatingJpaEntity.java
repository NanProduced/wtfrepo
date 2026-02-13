package com.wtfrepo.backend.arena.infra.persistence.entity;

import com.wtfrepo.backend.arena.domain.ArenaIpoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Arena-owned rating state for each specimen.
 *
 * <p>Schema migration is intentionally deferred until MVP review.
 */
@Getter
@Entity
@Table(name = "specimen_rating")
public class SpecimenRatingJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "elo_score", nullable = false)
  private int eloScore;

  @Column(name = "calibrated_score")
  private Integer calibratedScore;

  @Column(name = "matches_played", nullable = false)
  private int matchesPlayed;

  @Enumerated(EnumType.STRING)
  @Column(name = "ipo_status", nullable = false, length = 32)
  private ArenaIpoStatus ipoStatus;

  @Column(name = "k_factor", nullable = false)
  private int kFactor;

  @Column(name = "elo_open_today", nullable = false)
  private int eloOpenToday;

  @Column(name = "delta_r_7d_stddev")
  private Double deltaR7dStddev;

  @Column(name = "recent_appearances", nullable = false)
  private int recentAppearances;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenRatingJpaEntity() {}

  public static SpecimenRatingJpaEntity createDefault(String specimenId, int defaultElo) {
    SpecimenRatingJpaEntity entity = new SpecimenRatingJpaEntity();
    entity.specimenId = specimenId;
    entity.eloScore = defaultElo;
    entity.calibratedScore = null;
    entity.matchesPlayed = 0;
    entity.ipoStatus = ArenaIpoStatus.PRIVATE_BETA;
    entity.kFactor = 64;
    entity.eloOpenToday = defaultElo;
    entity.deltaR7dStddev = null;
    entity.recentAppearances = 0;
    entity.updatedAt = Instant.now();
    return entity;
  }
}

