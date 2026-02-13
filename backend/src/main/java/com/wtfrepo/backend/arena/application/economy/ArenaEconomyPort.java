package com.wtfrepo.backend.arena.application.economy;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;

/**
 * Arena-facing economy write/read port.
 *
 * <p>Non-M03 modules must never write wallet/ledger tables directly.
 */
public interface ArenaEconomyPort {

  /**
   * Deduct bug from wallet and return balance after deduction.
   *
   * <p>Economy write must remain idempotent by {@code idempotencyKey}. Snapshot values are passed so
   * economy ledger can persist audit fields required by contract governance.
   */
  long deductBug(
      String userId,
      int amount,
      String refType,
      String refId,
      String idempotencyKey,
      ArenaPolicySnapshot policySnapshot);

  long currentBalance(String userId);
}
