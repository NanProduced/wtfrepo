package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyWalletService;

public record WalletResponse(String userId, long balance, boolean dailyClaimed, int dailyAmount) {

  static WalletResponse from(EconomyWalletService.WalletView walletView) {
    return new WalletResponse(
        walletView.userId(),
        walletView.balance(),
        walletView.dailyClaimed(),
        walletView.dailyAmount());
  }
}

