package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Curated readme excerpt persisted for detail-page rendering. */
@Getter
@Entity
@Table(name = "specimen_readme_excerpt")
public class SpecimenReadmeExcerptJpaEntity {

  @Id
  @Column(name = "excerpt_id", nullable = false, length = 64)
  private String excerptId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "excerpt_type", nullable = false, length = 32)
  private String excerptType;

  @Column(name = "text", nullable = false, columnDefinition = "text")
  private String text;

  @Column(name = "translated_text_zh", columnDefinition = "text")
  private String translatedTextZh;

  @Column(name = "translation_meta_json", columnDefinition = "text")
  private String translationMetaJson;

  @Column(name = "candidate_id", length = 64)
  private String candidateId;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "curated_by", nullable = false, length = 64)
  private String curatedBy;

  @Column(name = "curated_at", nullable = false)
  private Instant curatedAt;

  protected SpecimenReadmeExcerptJpaEntity() {}

  public static SpecimenReadmeExcerptJpaEntity of(
      String specimenId,
      String excerptType,
      String text,
      String translatedTextZh,
      String translationMetaJson,
      String candidateId,
      int priority,
      String curatedBy) {
    SpecimenReadmeExcerptJpaEntity entity = new SpecimenReadmeExcerptJpaEntity();
    entity.excerptId = "exp_" + UUID.randomUUID();
    entity.specimenId = specimenId;
    entity.excerptType = excerptType;
    entity.text = text;
    entity.translatedTextZh = translatedTextZh;
    entity.translationMetaJson = translationMetaJson;
    entity.candidateId = candidateId;
    entity.priority = priority;
    entity.curatedBy = curatedBy;
    entity.curatedAt = Instant.now();
    return entity;
  }
}
