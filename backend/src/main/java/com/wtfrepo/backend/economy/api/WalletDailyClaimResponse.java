package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyWalletService;

public record WalletDailyClaimResponse(
    boolean claimed, int amount, long balanceAfter, boolean alreadyClaimed) {

  static WalletDailyClaimResponse from(EconomyWalletService.DailyClaimResult result) {
    return new WalletDailyClaimResponse(
        result.claimed(), result.amount(), result.balanceAfter(), result.alreadyClaimed());
  }
}

