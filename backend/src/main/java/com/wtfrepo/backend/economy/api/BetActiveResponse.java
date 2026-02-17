package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.BettingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BetActiveResponse(LocalDate date, List<Item> orders, long totalStaked) {

  static BetActiveResponse from(BettingService.ActiveBetsView value) {
    return new BetActiveResponse(
        value.date(), value.orders().stream().map(Item::from).toList(), value.totalStaked());
  }

  public record Item(
      String orderId,
      String specimenId,
      String specimenTitle,
      String direction,
      int amount,
      BigDecimal oddsAtPlace,
      BigDecimal currentOdds,
      String status) {

    static Item from(BettingService.ActiveBetItem value) {
      return new Item(
          value.orderId(),
          value.specimenId(),
          value.specimenTitle(),
          value.direction(),
          value.amount(),
          value.oddsAtPlace(),
          value.currentOdds(),
          value.status());
    }
  }
}
