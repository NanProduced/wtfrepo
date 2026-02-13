package com.wtfrepo.backend.arena.application.economy;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.application.support.ArenaContractProperties;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicy;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import com.wtfrepo.backend.specimen.application.SpecimenContractProperties;
import org.springframework.stereotype.Component;

/** Temporary policy adapter used before economy module is fully implemented. */
@Component
public class PropertyBackedArenaPolicyPort implements ArenaPolicyPort, ArenaRuntimePolicyPort {

  private final ArenaEconomyProperties properties;
  private final ArenaContractProperties arenaContractProperties;
  private final SpecimenContractProperties specimenContractProperties;

  public PropertyBackedArenaPolicyPort(
      ArenaEconomyProperties properties,
      ArenaContractProperties arenaContractProperties,
      SpecimenContractProperties specimenContractProperties) {
    this.properties = properties;
    this.arenaContractProperties = arenaContractProperties;
    this.specimenContractProperties = specimenContractProperties;
  }

  @Override
  public ArenaPolicySnapshot currentPolicySnapshot() {
    return new ArenaPolicySnapshot(
        properties.getVoteBugCost(),
        properties.getRakeRate(),
        properties.getMinBet(),
        properties.getPolicyVersion(),
        properties.getPolicySource());
  }

  @Override
  public ArenaRuntimePolicy currentArenaRuntimePolicy() {
    return new ArenaRuntimePolicy(
        arenaContractProperties.getBattleTtl(),
        arenaContractProperties.isSettlementInProgress(),
        specimenContractProperties.getDefaultElo());
  }
}
