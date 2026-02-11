package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Optional curated code highlights for specimen detail page. */
@Getter
@Entity
@Table(name = "specimen_code_highlight")
public class SpecimenCodeHighlightJpaEntity {

  @Id
  @Column(name = "highlight_id", nullable = false, length = 64)
  private String highlightId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "title", nullable = false, length = 256)
  private String title;

  @Column(name = "code_language", nullable = false, length = 64)
  private String codeLanguage;

  @Column(name = "snippet", nullable = false, columnDefinition = "text")
  private String snippet;

  @Column(name = "explain_text", nullable = false, columnDefinition = "text")
  private String explainText;

  @Column(name = "candidate_id", length = 64)
  private String candidateId;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SpecimenCodeHighlightJpaEntity() {}

  public static SpecimenCodeHighlightJpaEntity of(
      String specimenId,
      String title,
      String codeLanguage,
      String snippet,
      String explainText,
      String candidateId,
      int priority) {
    SpecimenCodeHighlightJpaEntity entity = new SpecimenCodeHighlightJpaEntity();
    entity.highlightId = "hl_" + UUID.randomUUID();
    entity.specimenId = specimenId;
    entity.title = title;
    entity.codeLanguage = codeLanguage;
    entity.snippet = snippet;
    entity.explainText = explainText;
    entity.candidateId = candidateId;
    entity.priority = priority;
    entity.status = "PUBLISHED";
    entity.updatedAt = Instant.now();
    return entity;
  }
}
