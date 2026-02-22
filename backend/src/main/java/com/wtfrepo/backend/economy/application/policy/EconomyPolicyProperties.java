package com.wtfrepo.backend.economy.application.policy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Temporary property-backed economy policy values.
 *
 * <p>TODO(M03-economy): replace property source with admin-published DB policy source.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.economy.policy")
public class EconomyPolicyProperties {

  private int dailyClaimAmount = 500;
  private int voteCost = 100;
  private int dailyGameBugCap = 2000;
  private String policyVersion = "economy-policy-property-v1";
  private String policySource = "property";
}
