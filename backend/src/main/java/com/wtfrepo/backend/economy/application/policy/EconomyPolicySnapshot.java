package com.wtfrepo.backend.economy.application.policy;

/** Runtime economy policy snapshot persisted into transactional facts. */
public record EconomyPolicySnapshot(int dailyClaimAmount, String policyVersion, String policySource) {}

