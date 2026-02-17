package com.wtfrepo.backend.arena.application.economy;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyWalletJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Temporary no-op economy adapter to keep arena call sites stable before M03 implementation.
 *
 * <p>TODO(M03-economy): replace this adapter with a real M03-backed implementation.
 */
@Deprecated(forRemoval = true)
@Component
@ConditionalOnMissingBean(EconomyWalletJpaRepository.class)
public class NoOpArenaEconomyPort implements ArenaEconomyPort {

  @Override
  public long deductBug(
      String userId,
      int amount,
      String refType,
      String refId,
      String idempotencyKey,
      ArenaPolicySnapshot policySnapshot) {
    // Intentionally empty in scaffold phase.
    return 0L;
  }

  @Override
  public long currentBalance(String userId) {
    // TODO(M03-economy): return balance from economy wallet source of truth.
    return 0L;
  }
}
