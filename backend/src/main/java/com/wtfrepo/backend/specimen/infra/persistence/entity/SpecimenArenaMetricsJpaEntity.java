package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Frequently-changing arena-related metrics for a specimen.
 *
 * <p>This split keeps write-hot fields away from the core specimen aggregate to reduce update
 * contention and to support future ownership migration to M01 arena module.
 *
 * <p>Current placement is pragmatic for M04 delivery because watchlist/user reads already depend
 * on these values. Treat this entity as an integration seam rather than final domain ownership.
 *
 * <p>TODO(M01-arena): re-evaluate final module ownership and potentially move this entity into
 * arena module after M01 boundaries are finalized.
 */
@Getter
@Entity
@Table(name = "specimen_arena_metrics")
public class SpecimenArenaMetricsJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  /** Arena rating baseline (Elo-like), mutable by arena battles. */
  @Column(name = "elo", nullable = false)
  private int elo;

  /** Lightweight popularity signal used by list sorting/UX hints. */
  @Column(name = "hype", nullable = false)
  private double hype;

  /** Total counted votes participating in arena ranking. */
  @Column(name = "votes", nullable = false)
  private long votes;

  /** Recent 24h rating delta for trend display. */
  @Column(name = "delta_24h", nullable = false)
  private int delta24h;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenArenaMetricsJpaEntity() {}

  public static SpecimenArenaMetricsJpaEntity createDefault(String specimenId, int defaultElo) {
    SpecimenArenaMetricsJpaEntity entity = new SpecimenArenaMetricsJpaEntity();
    entity.specimenId = specimenId;
    entity.elo = defaultElo;
    entity.hype = 0.0D;
    entity.votes = 0L;
    entity.delta24h = 0;
    entity.updatedAt = Instant.now();
    return entity;
  }

  public void applyArenaVoteDelta(int eloDelta) {
    this.elo += eloDelta;
    this.votes += 1;
    this.delta24h += eloDelta;
    this.updatedAt = Instant.now();
  }

  public void applyEloUpdate(int eloBefore, int eloAfter) {
    int delta = eloAfter - eloBefore;
    this.elo = eloAfter;
    this.votes += 1;
    this.delta24h += delta;
    this.updatedAt = Instant.now();
  }
}
