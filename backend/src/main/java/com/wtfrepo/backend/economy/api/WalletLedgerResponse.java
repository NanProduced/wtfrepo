package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyWalletService;
import java.time.Instant;
import java.util.List;

/** Cursor-based wallet ledger response. */
public record WalletLedgerResponse(List<Item> items, String nextCursor, boolean hasMore) {

  static WalletLedgerResponse from(EconomyWalletService.LedgerPageView pageView) {
    return new WalletLedgerResponse(
        pageView.items().stream().map(Item::from).toList(), pageView.nextCursor(), pageView.hasMore());
  }

  public record Item(
      String ledgerId,
      long delta,
      long balanceAfter,
      String reason,
      String refId,
      String refType,
      Instant createdAt) {

    static Item from(EconomyWalletService.LedgerItemView itemView) {
      return new Item(
          itemView.ledgerId(),
          itemView.delta(),
          itemView.balanceAfter(),
          itemView.reason(),
          itemView.refId(),
          itemView.refType(),
          itemView.createdAt());
    }
  }
}

