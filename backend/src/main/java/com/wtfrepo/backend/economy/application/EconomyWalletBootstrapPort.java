package com.wtfrepo.backend.economy.application;

/**
 * Provides initial wallet balance when economy wallet row is not initialized yet.
 *
 * <p>TODO(M03-economy): remove this bootstrap bridge after full migration to economy-owned wallet.
 */
public interface EconomyWalletBootstrapPort {

  long initialBalanceFor(String userId);
}

