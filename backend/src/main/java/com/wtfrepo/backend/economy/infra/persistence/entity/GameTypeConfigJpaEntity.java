package com.wtfrepo.backend.economy.infra.persistence.entity;

import com.wtfrepo.backend.economy.domain.GameTypeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Configurable game type metadata owned by M03 economy.
 *
 * <p>契约要求小游戏类型从数据库读取，避免前端/后端硬编码列表。
 */
@Getter
@Entity
@Table(name = "game_type_config")
public class GameTypeConfigJpaEntity {

  @Id
  @Column(name = "game_type_key", nullable = false, length = 64)
  private String gameTypeKey;

  @Column(name = "name_zh", nullable = false, length = 128)
  private String nameZh;

  @Column(name = "name_en", nullable = false, length = 128)
  private String nameEn;

  @Column(name = "description", length = 512)
  private String description;

  @Column(name = "icon", length = 64)
  private String icon;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private GameTypeStatus status;

  @Column(name = "bug_formula", nullable = false, length = 64)
  private String bugFormula;

  @Column(name = "bug_formula_params", columnDefinition = "text")
  private String bugFormulaParams;

  @Column(name = "max_score_per_second", precision = 10, scale = 4)
  private BigDecimal maxScorePerSecond;

  @Column(name = "min_duration_ms")
  private Integer minDurationMs;

  @Column(name = "daily_play_limit")
  private Integer dailyPlayLimit;

  @Column(name = "ui_meta", columnDefinition = "text")
  private String uiMeta;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected GameTypeConfigJpaEntity() {}
}

