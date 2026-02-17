package com.wtfrepo.backend.arena.application.support;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime controls for scheduled arena settlement window. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.settlement")
public class ArenaSettlementProperties {

  /**
   * Enables scheduled daily settlement execution.
   *
   * <p>Default stays disabled to avoid accidental execution before infrastructure is ready.
   */
  private boolean enabled = false;

  /**
   * UTC cron for running phase A/B settlement.
   *
   * <p>Contract target is UTC 00:00.
   */
  private String snapshotCron = "0 0 0 * * *";
}
