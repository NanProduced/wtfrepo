package com.wtfrepo.backend.auth.application.economy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Temporary economy-related properties used by auth module before M03 is integrated.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth.economy")
public class AuthEconomyProperties {

  /**
   * Temporary default for MVP.
   * TODO(M03-economy): source this from economy module and multiplier policy.
   */
  private int initialBugGrant = 5;
}
