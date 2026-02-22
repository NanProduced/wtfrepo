package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.BettingService;
import java.time.LocalDate;
import java.util.List;

/** API response for contract endpoint {@code GET /api/v1/settlement/today}. */
public record SettlementTodayResponse(
    LocalDate date, List<Item> settlements, String nextCursor, boolean hasMore) {

  static SettlementTodayResponse from(BettingService.SettlementTodayView view) {
    return new SettlementTodayResponse(
        view.date(),
        view.settlements().stream().map(Item::from).toList(),
        view.nextCursor(),
        view.hasMore());
  }

  public record Item(
      String specimenId,
      String specimenTitle,
      int eloOpen,
      int eloClose,
      int deltaR,
      String outcome,
      boolean isMoonDoom,
      List<OrderItem> myOrders) {

    static Item from(BettingService.SettlementItem value) {
      return new Item(
          value.specimenId(),
          value.specimenTitle(),
          value.eloOpen(),
          value.eloClose(),
          value.deltaR(),
          value.outcome(),
          value.isMoonDoom(),
          value.myOrders().stream().map(OrderItem::from).toList());
    }
  }

  public record OrderItem(
    String orderId,
    String direction,
    int amount,
    String status,
    long payout,
    long moonDoomBonus) {

  static OrderItem from(BettingService.SettlementOrderItem value) {
    return new OrderItem(
        value.orderId(),
        value.direction(),
        value.amount(),
        value.status(),
        value.payout(),
        value.moonDoomBonus());
  }
}
}

