package com.wtfrepo.backend.arena.application.economy;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaExceptions;
import com.wtfrepo.backend.economy.application.EconomyWalletService;
import com.wtfrepo.backend.economy.application.EconomyWalletService.DeductCommand;
import com.wtfrepo.backend.economy.application.EconomyWalletService.DeductPolicySnapshot;
import com.wtfrepo.backend.economy.application.EconomyWalletService.InsufficientBalanceException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Arena -> Economy bridge adapter.
 *
 * <p>This keeps arena write paths depending on a local port while delegating the real wallet/ledger
 * transaction to economy module implementation.
 */
@Component
@Primary
public class M03BackedArenaEconomyPort implements ArenaEconomyPort {

  private final EconomyWalletService economyWalletService;

  public M03BackedArenaEconomyPort(EconomyWalletService economyWalletService) {
    this.economyWalletService = economyWalletService;
  }

  @Override
  public long deductBug(
      String userId,
      int amount,
      String refType,
      String refId,
      String idempotencyKey,
      ArenaPolicySnapshot policySnapshot) {
    try {
      return economyWalletService.deduct(
          new DeductCommand(
              userId,
              amount,
              refType,
              refId,
              idempotencyKey,
              new DeductPolicySnapshot(
                  policySnapshot.bugCost(),
                  policySnapshot.rakeRate(),
                  policySnapshot.minBet(),
                  policySnapshot.policyVersion(),
                  policySnapshot.policySource())));
    } catch (InsufficientBalanceException ex) {
      throw ArenaExceptions.insufficientBug(ArenaConstants.Message.INSUFFICIENT_BUG);
    }
  }

  @Override
  public long currentBalance(String userId) {
    return economyWalletService.currentBalance(userId);
  }
}

