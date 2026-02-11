package com.wtfrepo.backend.specimen.infra.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite id for tag definitions under dimension scope. */
public class TagDefinitionJpaId implements Serializable {

  private String dimensionKey;
  private String tagKey;

  public TagDefinitionJpaId() {}

  public TagDefinitionJpaId(String dimensionKey, String tagKey) {
    this.dimensionKey = dimensionKey;
    this.tagKey = tagKey;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TagDefinitionJpaId that)) {
      return false;
    }
    return Objects.equals(dimensionKey, that.dimensionKey)
        && Objects.equals(tagKey, that.tagKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dimensionKey, tagKey);
  }
}
