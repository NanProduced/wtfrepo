package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyWalletService;

public record WalletResponse(
    String userId,
    long balance,
    long totalEarned,
    long totalSpent,
    boolean dailyClaimed,
    int dailyAmount,
    int voteCost) {

  static WalletResponse from(EconomyWalletService.WalletView walletView) {
    return new WalletResponse(
        walletView.userId(),
        walletView.balance(),
        walletView.totalEarned(),
        walletView.totalSpent(),
        walletView.dailyClaimed(),
        walletView.dailyAmount(),
        walletView.voteCost());
  }
}
