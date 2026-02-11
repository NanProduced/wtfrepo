package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Dimension-level tag schema.
 *
 * <p>This table defines "containers" of tags (for example mood/style/risk) and stores only
 * dimension concerns: selection constraints, ordering, and dimension UI metadata.
 *
 * <p>It does not store concrete selectable tags. Concrete options are stored in
 * {@link TagDefinitionJpaEntity} and linked by {@code dimensionKey}. Keeping this separation avoids
 * mixing "how a dimension behaves" with "which tags are available".
 */
@Getter
@Entity
@Table(name = "tag_dimension")
public class TagDimensionJpaEntity {

  /** Stable key referenced by {@link TagDefinitionJpaEntity#dimensionKey}. */
  @Id
  @Column(name = "dimension_key", nullable = false, length = 64)
  private String dimensionKey;

  @Column(name = "name_zh", nullable = false, length = 128)
  private String nameZh;

  @Column(name = "name_en", nullable = false, length = 128)
  private String nameEn;

  /** Human-readable explanation of this dimension's curation/matching semantics. */
  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "select_mode", nullable = false, length = 32)
  private String selectMode;

  /** Minimum tag count that admin curation must select under this dimension. */
  @Column(name = "min_select")
  private Integer minSelect;

  /** Maximum tag count that admin curation is allowed to select under this dimension. */
  @Column(name = "max_select")
  private Integer maxSelect;

  @Column(name = "required", nullable = false)
  private boolean required;

  /** Whether this dimension is included by the matching algorithm. */
  @Column(name = "match_enabled", nullable = false)
  private boolean matchEnabled;

  /** Optional default weight used by matching policy fallback. */
  @Column(name = "default_weight", precision = 10, scale = 4)
  private BigDecimal defaultWeight;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /**
   * Dimension-level visual metadata for frontend rendering.
   *
   * <p>Examples: dimension icon, hint copy, style token.
   */
  @Column(name = "ui_meta_json", columnDefinition = "text")
  private String uiMetaJson;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TagDimensionJpaEntity() {}
}
