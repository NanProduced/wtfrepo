package com.wtfrepo.backend.economy.application.support;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime switches for M03 betting settlement orchestrator. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.betting.settlement")
public class BettingSettlementProperties {

  /** Enables scheduled settlement orchestrator. */
  private boolean enabled = false;

  /** UTC cron expression for settlement orchestrator trigger. */
  private String orchestratorCron = "30 0 0 * * *";

  /** Maximum retry attempts before fallback force-settle is triggered. */
  private int retryMaxAttempts = 3;

  /**
   * Initial retry backoff in milliseconds.
   *
   * <p>Backoff grows exponentially by attempt index and is capped by {@link #retryMaxBackoffMs}.
   * Setting this value to {@code 0} disables sleep while keeping retry ordering logic.
   */
  private long retryInitialBackoffMs = 0L;

  /** Maximum backoff cap for exponential retries in milliseconds. */
  private long retryMaxBackoffMs = 2000L;

  /**
   * Total retry deadline budget for one settlement run in seconds.
   *
   * <p>After deadline is reached, unsettled pools move directly to fallback force-settle.
   */
  private long retryDeadlineSeconds = 300L;
}
