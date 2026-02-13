package com.wtfrepo.backend.arena.application.economy;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;

/**
 * Arena-facing policy read port for transaction-policy snapshot fields.
 *
 * <p>Runtime controls such as battle TTL/settlement/initial Elo are exposed via
 * {@code ArenaRuntimePolicyPort}.
 */
public interface ArenaPolicyPort {

  ArenaPolicySnapshot currentPolicySnapshot();
}
