package com.wtfrepo.backend.economy.application.policy;

/** Runtime economy policy snapshot persisted into transactional facts. */
public record EconomyPolicySnapshot(
    int dailyClaimAmount,
    int voteCost,
    int dailyGameBugCap,
    String policyVersion,
    String policySource) {}
