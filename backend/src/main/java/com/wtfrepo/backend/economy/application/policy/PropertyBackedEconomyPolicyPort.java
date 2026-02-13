package com.wtfrepo.backend.economy.application.policy;

import org.springframework.stereotype.Component;

/** Property-backed economy policy implementation used before admin policy center is ready. */
@Component
public class PropertyBackedEconomyPolicyPort implements EconomyPolicyPort {

  private final EconomyPolicyProperties properties;

  public PropertyBackedEconomyPolicyPort(EconomyPolicyProperties properties) {
    this.properties = properties;
  }

  @Override
  public EconomyPolicySnapshot currentPolicySnapshot() {
    return new EconomyPolicySnapshot(
        properties.getDailyClaimAmount(), properties.getPolicyVersion(), properties.getPolicySource());
  }
}

