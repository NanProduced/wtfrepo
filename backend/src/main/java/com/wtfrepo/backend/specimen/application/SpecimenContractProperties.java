package com.wtfrepo.backend.specimen.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.specimen.contract")
public class SpecimenContractProperties {

  private int watchlistMaxItems = 200;
  private int watchlistDefaultLimit = 20;
  private int watchlistMaxLimit = 50;
  private Duration adminIdempotencyTtl = Duration.ofHours(24);
  private String tagConfigVersion = "tagcfg_2026_02_10_01";
  /**
   * Current published match profile version consumed by M01/Arena.
   *
   * <p>TODO(M04-config): switch to persisted match_profile table once config publish workflow lands.
   */
  private String publishedMatchProfileVersion = "v1.0.0";
  /** Dimension key mapped to Arena species field in match candidate read model. */
  private String matchSpeciesDimensionKey = "species";
  /** Dimension key mapped to Arena diagnosis tags field in match candidate read model. */
  private String matchDiagnosisDimensionKey = "diagnosis";
  /**
   * Temporary default Elo baseline for metrics bootstrap.
   *
   * <p>TODO(M01-arena): once arena module owns rating initialization, move this setting under M01
   * policy source and remove M04 ownership.
   */
  private int defaultElo = 1500;
}
