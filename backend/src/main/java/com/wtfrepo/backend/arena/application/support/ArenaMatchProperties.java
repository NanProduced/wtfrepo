package com.wtfrepo.backend.arena.application.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable arena duel match profile parameters. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.match")
public class ArenaMatchProperties {

  private String profileVersion = "v1.0.0";

  private String speciesDimensionKey = "species";

  private String diagnosisDimensionKey = "diagnosis";

  private int sameSpeciesScore = 100;

  private int adjacentSpeciesScore = 60;

  private int crossSpeciesScore = 0;

  private int diagnosisBaseScore = 15;

  private int diagnosisBonusCap = 30;

  private double cooldownAlpha = 0.3D;

  /**
   * When enabled, duel service first loads candidates from precomputed {@code specimen_match_pair}.
   */
  private boolean pairFirstEnabled = true;

  /**
   * Enables runtime pair composition fallback when precomputed rows are unavailable.
   *
   * <p>This switch is mainly used as a rollout guard while pair rebuild pipeline is being verified.
   */
  private boolean runtimePairFallbackEnabled = true;

  /**
   * Sliding time window for recomputing {@code recent_appearances}.
   *
   * <p>Contract default is 24h and this value belongs to match profile runtime config.
   */
  private Duration recentAppearanceWindow = Duration.ofHours(24);

  private int resetExcludeThreshold = 4;

  private List<String> adjacentSpeciesPairs = new ArrayList<>();
}
