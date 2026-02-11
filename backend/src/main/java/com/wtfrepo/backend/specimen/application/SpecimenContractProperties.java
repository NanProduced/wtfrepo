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
}
