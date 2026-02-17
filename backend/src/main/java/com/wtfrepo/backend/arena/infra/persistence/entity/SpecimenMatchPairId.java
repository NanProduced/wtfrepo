package com.wtfrepo.backend.arena.infra.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@code specimen_match_pair}. */
public class SpecimenMatchPairId implements Serializable {

  private String leftSpecimenId;

  private String rightSpecimenId;

  public SpecimenMatchPairId() {}

  public SpecimenMatchPairId(String leftSpecimenId, String rightSpecimenId) {
    this.leftSpecimenId = leftSpecimenId;
    this.rightSpecimenId = rightSpecimenId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SpecimenMatchPairId that)) {
      return false;
    }
    return Objects.equals(leftSpecimenId, that.leftSpecimenId)
        && Objects.equals(rightSpecimenId, that.rightSpecimenId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(leftSpecimenId, rightSpecimenId);
  }
}
