package com.wtfrepo.backend.economy.application.policy;

/** Economy-facing policy read port. */
public interface EconomyPolicyPort {

  EconomyPolicySnapshot currentPolicySnapshot();
}

