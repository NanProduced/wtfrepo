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

  public static SpecimenRatingJpaEntity createFromLegacyMetrics(
      String specimenId, int eloScore, long matchesPlayed) {
    SpecimenRatingJpaEntity entity = new SpecimenRatingJpaEntity();
    entity.specimenId = specimenId;
    entity.eloScore = eloScore;
    entity.calibratedScore = null;
    entity.matchesPlayed = toMatchesPlayed(matchesPlayed);
    entity.ipoStatus = entity.matchesPlayed >= 10 ? ArenaIpoStatus.IPO : ArenaIpoStatus.PRIVATE_BETA;
    entity.kFactor = resolveKFactor(entity.matchesPlayed);
    entity.eloOpenToday = eloScore;
    entity.deltaR7dStddev = null;
    entity.recentAppearances = 0;
    entity.updatedAt = Instant.now();
    return entity;
  }

  public void applyVoteDelta(int eloDelta) {
    this.eloScore += eloDelta;
    this.matchesPlayed += 1;

    if (this.matchesPlayed == 10) {
      // IPO first-day baseline is established in the same transaction as the 10th vote.
      this.ipoStatus = ArenaIpoStatus.IPO;
      this.calibratedScore = this.eloScore;
      this.eloOpenToday = this.eloScore;
    }

    this.kFactor = resolveKFactor(this.matchesPlayed);
    this.updatedAt = Instant.now();
  }

  public void applyGlobalCorrection(int correction) {
    this.eloScore += correction;
    this.updatedAt = Instant.now();
  }

  public void rollOpenToCurrentElo() {
    this.eloOpenToday = this.eloScore;
    this.updatedAt = Instant.now();
  }

  /**
   * Refreshes match-profile dependent derived metrics in a single write.
   *
   * <p>Both fields are maintained by periodic batch recomputation jobs instead of vote-path
   * transactional increments, which keeps vote latency stable and avoids lock amplification.
   */
  public void refreshMatchProfileMetrics(int recentAppearances, double deltaR7dStddev) {
    this.recentAppearances = Math.max(0, recentAppearances);
    this.deltaR7dStddev = sanitizeDeltaR7dStddev(deltaR7dStddev);
    this.updatedAt = Instant.now();
  }

  private static int toMatchesPlayed(long matchesPlayed) {
    if (matchesPlayed <= 0) {
      return 0;
    }
    return matchesPlayed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) matchesPlayed;
  }

  private static int resolveKFactor(int matchesPlayed) {
    if (matchesPlayed < 10) {
      return 64;
    }
    if (matchesPlayed < 30) {
      return 32;
    }
    return 16;
  }

  private static double sanitizeDeltaR7dStddev(double value) {
    if (!Double.isFinite(value) || value < 0.0D) {
      return 0.0D;
    }
    return value;
  }
}
