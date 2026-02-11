package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/** Official commentary curated by admin for specimen detail and arena context. */
@Getter
@Entity
@Table(name = "specimen_official_commentary")
public class SpecimenOfficialCommentaryJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  /** Chinese one-liner for zh UI. */
  @Column(name = "one_liner_zh", columnDefinition = "text")
  private String oneLinerZh;

  /** English one-liner for en UI. */
  @Column(name = "one_liner_en", columnDefinition = "text")
  private String oneLinerEn;

  /** Chinese arena reason for zh UI. */
  @Column(name = "arena_reason_zh", columnDefinition = "text")
  private String arenaReasonZh;

  /** English arena reason for en UI. */
  @Column(name = "arena_reason_en", columnDefinition = "text")
  private String arenaReasonEn;

  @Column(name = "updated_by", nullable = false, length = 64)
  private String updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenOfficialCommentaryJpaEntity() {}

  public static SpecimenOfficialCommentaryJpaEntity createOrUpdate(
      String specimenId,
      String oneLinerZh,
      String oneLinerEn,
      String arenaReasonZh,
      String arenaReasonEn,
      String updatedBy) {
    SpecimenOfficialCommentaryJpaEntity entity = new SpecimenOfficialCommentaryJpaEntity();
    entity.specimenId = specimenId;
    entity.oneLinerZh = oneLinerZh;
    entity.oneLinerEn = oneLinerEn;
    entity.arenaReasonZh = arenaReasonZh;
    entity.arenaReasonEn = arenaReasonEn;
    entity.updatedBy = updatedBy;
    entity.updatedAt = Instant.now();
    return entity;
  }
}
