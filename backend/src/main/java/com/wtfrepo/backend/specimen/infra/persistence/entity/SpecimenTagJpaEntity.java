package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/** Mapping entity for specimen and configured tags. */
@Getter
@Entity
@IdClass(SpecimenTagJpaId.class)
@Table(name = "specimen_tag")
public class SpecimenTagJpaEntity {

  @Id
  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Id
  @Column(name = "dimension_key", nullable = false, length = 64)
  private String dimensionKey;

  @Id
  @Column(name = "tag_key", nullable = false, length = 128)
  private String tagKey;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  protected SpecimenTagJpaEntity() {}

  public static SpecimenTagJpaEntity of(String specimenId, String dimensionKey, String tagKey) {
    SpecimenTagJpaEntity entity = new SpecimenTagJpaEntity();
    entity.specimenId = specimenId;
    entity.dimensionKey = dimensionKey;
    entity.tagKey = tagKey;
    entity.assignedAt = Instant.now();
    return entity;
  }
}
