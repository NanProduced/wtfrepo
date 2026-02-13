package com.wtfrepo.backend.arena.application.policy;

import java.math.BigDecimal;

/**
 * Immutable policy snapshot persisted alongside arena transactions for auditability.
 */
public record ArenaPolicySnapshot(
    int bugCost, BigDecimal rakeRate, int minBet, String policyVersion, String policySource) {}