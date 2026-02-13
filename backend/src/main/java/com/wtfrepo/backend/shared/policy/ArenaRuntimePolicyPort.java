package com.wtfrepo.backend.shared.policy;

/**
 * Shared read port for arena runtime policy values.
 *
 * <p>Non-arena modules (for example specimen bootstrap) should consume arena runtime parameters
 * through this port instead of hard-coding or duplicating local configuration fields.
 */
public interface ArenaRuntimePolicyPort {

  ArenaRuntimePolicy currentArenaRuntimePolicy();
}

