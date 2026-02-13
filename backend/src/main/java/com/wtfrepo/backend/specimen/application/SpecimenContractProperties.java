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
   * Temporary default Elo baseline for metrics bootstrap.
   *
   * <p>TODO(M01-arena): once arena module owns rating initialization, move this setting under M01
   * policy source and remove M04 ownership.
   */
  private int defaultElo = 1500;
}
