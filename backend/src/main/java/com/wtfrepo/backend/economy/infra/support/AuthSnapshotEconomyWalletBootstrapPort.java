package com.wtfrepo.backend.economy.infra.support;

import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import com.wtfrepo.backend.economy.application.EconomyWalletBootstrapPort;
import org.springframework.stereotype.Component;

/**
 * Temporary bootstrap adapter that reuses auth user balance snapshot for wallet initialization.
 *
 * <p>TODO(M03-economy): delete this bridge once economy wallet is the only source of truth.
 */
@Component
public class AuthSnapshotEconomyWalletBootstrapPort implements EconomyWalletBootstrapPort {

  private final AuthUserJpaRepository authUserJpaRepository;

  public AuthSnapshotEconomyWalletBootstrapPort(AuthUserJpaRepository authUserJpaRepository) {
    this.authUserJpaRepository = authUserJpaRepository;
  }

  @Override
  public long initialBalanceFor(String userId) {
    return authUserJpaRepository.findById(userId).map(user -> user.getBugBalance()).orElse(0L);
  }
}

