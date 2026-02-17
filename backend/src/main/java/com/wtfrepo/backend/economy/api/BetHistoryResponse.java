package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.BettingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BetHistoryResponse(List<Item> orders, String nextCursor, boolean hasMore) {

  static BetHistoryResponse from(BettingService.HistoryBetsView value) {
    return new BetHistoryResponse(
        value.orders().stream().map(Item::from).toList(), value.nextCursor(), value.hasMore());
  }

  public record Item(
      String orderId,
      String specimenId,
      String specimenTitle,
      String direction,
      int amount,
      BigDecimal oddsAtPlace,
      String status,
      Long payout,
      long moonDoomBonus,
      LocalDate settleDate,
      Instant settledAt) {

    static Item from(BettingService.HistoryBetItem value) {
      return new Item(
          value.orderId(),
          value.specimenId(),
          value.specimenTitle(),
          value.direction(),
          value.amount(),
          value.oddsAtPlace(),
          value.status(),
          value.payout(),
          value.moonDoomBonus(),
          value.settleDate(),
          value.settledAt());
    }
  }
}
