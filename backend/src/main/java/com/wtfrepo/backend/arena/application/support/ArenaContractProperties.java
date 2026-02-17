package com.wtfrepo.backend.arena.application.support;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Arena contract properties used by API and application layer. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.contract")
public class ArenaContractProperties {

  /**
   * Battle id validity duration from issuance.
   *
   * <p>Contract default is 10 minutes.
   */
  private Duration battleTtl = Duration.ofMinutes(10);

  /**
   * Shared secret used to validate stateless battle id signatures.
   *
   * <p>TODO(M01-arena): rotate secret via managed secret store before production launch.
   */
  private String battleIdSecret = "change-this-arena-battle-id-secret";

  /**
   * Whether vote endpoint should reject writes due to settlement window.
   */
  private boolean settlementInProgress = false;
}
