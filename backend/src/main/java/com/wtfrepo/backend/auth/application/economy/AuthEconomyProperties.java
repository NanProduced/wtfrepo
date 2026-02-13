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
   * Temporary bootstrap grant before M03 economy module is fully integrated.
   *
   * <p>TODO(M03-economy): source this from unified economy policy publishing (DB + admin), then
   * remove auth-local property ownership.
   */
  private int initialBugGrant;
}
