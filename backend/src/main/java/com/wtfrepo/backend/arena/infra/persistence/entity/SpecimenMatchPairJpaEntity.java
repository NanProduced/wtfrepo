package com.wtfrepo.backend.arena.infra.persistence.entity;

import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Precomputed specimen pair for duel matching.
 *
 * <p>This row stores static match metadata from Arena match profile and is rebuilt by background
 * maintenance flows. Schema migration is intentionally deferred until MVP entity review is done.
 */
@Getter
@Entity
@IdClass(SpecimenMatchPairId.class)
@Table(name = "specimen_match_pair")
public class SpecimenMatchPairJpaEntity {

  @Id
  @Column(name = "left_specimen_id", nullable = false, length = 64)
  private String leftSpecimenId;

  @Id
  @Column(name = "right_specimen_id", nullable = false, length = 64)
  private String rightSpecimenId;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false, length = 32)
  private ArenaMatchType matchType;

  @Column(name = "match_score", nullable = false)
  private int matchScore;

  @Column(name = "match_profile_version", nullable = false, length = 64)
  private String matchProfileVersion;

  @Column(name = "computed_at", nullable = false)
  private Instant computedAt;

  protected SpecimenMatchPairJpaEntity() {}

  /**
   * Creates one pair row with lexicographic key normalization.
   *
   * <p>Contract requires {@code left_specimen_id < right_specimen_id} for stable key semantics
   * and alignment with client-side excludeCombinations canonical ordering.
   */
  public static SpecimenMatchPairJpaEntity create(
      String specimenA,
      String specimenB,
      ArenaMatchType matchType,
      int matchScore,
      String matchProfileVersion,
      Instant computedAt) {
    if (specimenA == null
        || specimenB == null
        || specimenA.isBlank()
        || specimenB.isBlank()
        || specimenA.equals(specimenB)) {
      throw new IllegalArgumentException("Invalid specimen pair");
    }

    String normalizedA = specimenA.trim();
    String normalizedB = specimenB.trim();
    String left = normalizedA.compareTo(normalizedB) <= 0 ? normalizedA : normalizedB;
    String right = normalizedA.compareTo(normalizedB) <= 0 ? normalizedB : normalizedA;

    SpecimenMatchPairJpaEntity entity = new SpecimenMatchPairJpaEntity();
    entity.leftSpecimenId = left;
    entity.rightSpecimenId = right;
    entity.matchType = matchType;
    entity.matchScore = matchScore;
    entity.matchProfileVersion = matchProfileVersion;
    entity.computedAt = computedAt;
    return entity;
  }

  public void refresh(int matchScore, String matchProfileVersion, Instant computedAt) {
    this.matchScore = matchScore;
    this.matchProfileVersion = matchProfileVersion;
    this.computedAt = computedAt;
  }
}
