package com.wtfrepo.backend.specimen.infra.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite id for specimen tag assignments. */
public class SpecimenTagJpaId implements Serializable {

  private String specimenId;
  private String dimensionKey;
  private String tagKey;

  public SpecimenTagJpaId() {}

  public SpecimenTagJpaId(String specimenId, String dimensionKey, String tagKey) {
    this.specimenId = specimenId;
    this.dimensionKey = dimensionKey;
    this.tagKey = tagKey;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SpecimenTagJpaId that)) {
      return false;
    }
    return Objects.equals(specimenId, that.specimenId)
        && Objects.equals(dimensionKey, that.dimensionKey)
        && Objects.equals(tagKey, that.tagKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(specimenId, dimensionKey, tagKey);
  }
}
