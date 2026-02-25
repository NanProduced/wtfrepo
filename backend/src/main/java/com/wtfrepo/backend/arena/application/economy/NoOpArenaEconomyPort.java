package com.wtfrepo.backend.arena.application.economy;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyWalletJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Compatibility no-op adapter used only when economy persistence beans are absent.
 *
 * <p>In normal runtime, {@link EconomyBackedArenaEconomyPort} should be selected. This fallback keeps
 * lightweight test/scaffold contexts bootable without pulling full economy infrastructure.
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
    // Intentionally empty in scaffold stage.
    return 0L;
  }

  @Override
  public long currentBalance(String userId) {
    // Fallback mode intentionally returns 0 and should never be used in production runtime.
    return 0L;
  }
}
