package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Concrete selectable tag under one dimension.
 *
 * <p>{@link TagDimensionJpaEntity} describes dimension behavior (constraints/ordering/match
 * switches), while this table describes concrete choices shown to admin/frontend, including
 * display names, order and optional matching weight.
 *
 * <p>Runtime matching parameters can be snapshotted by profile/version tables later; therefore
 * this table should be treated as source configuration rather than immutable algorithm history.
 */
@Getter
@Entity
@IdClass(TagDefinitionJpaId.class)
@Table(name = "tag_definition")
public class TagDefinitionJpaEntity {

  /** Foreign-key-like reference to {@link TagDimensionJpaEntity#dimensionKey}. */
  @Id
  @Column(name = "dimension_key", nullable = false, length = 64)
  private String dimensionKey;

  /** Stable key of this tag inside the dimension. */
  @Id
  @Column(name = "tag_key", nullable = false, length = 128)
  private String tagKey;

  @Column(name = "name_zh", nullable = false, length = 128)
  private String nameZh;

  @Column(name = "name_en", nullable = false, length = 128)
  private String nameEn;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  /** Optional configurable contribution weight for matching. */
  @Column(name = "match_weight", precision = 10, scale = 4)
  private BigDecimal matchWeight;

  /**
   * Visual metadata payload used by frontend rendering.
   *
   * <p>Examples: color token, icon, badge style, tooltip.
   */
  @Column(name = "ui_meta_json", columnDefinition = "text")
  private String uiMetaJson;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TagDefinitionJpaEntity() {}
}
