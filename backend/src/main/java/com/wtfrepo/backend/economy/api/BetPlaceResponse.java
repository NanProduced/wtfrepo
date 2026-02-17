package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.BettingService;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BetPlaceResponse(
    String orderId,
    String specimenId,
    String direction,
    int amount,
    BigDecimal oddsAtPlace,
    LocalDate settleDate,
    String status,
    long walletBalanceAfter) {

  static BetPlaceResponse from(BettingService.PlaceBetResult value) {
    return new BetPlaceResponse(
        value.orderId(),
        value.specimenId(),
        value.direction(),
        value.amount(),
        value.oddsAtPlace(),
        value.settleDate(),
        value.status(),
        value.walletBalanceAfter());
  }
}
