package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyGameService;
import java.util.List;
import java.util.Map;

/** Game-type listing payload used by frontend dynamic game portal. */
public record GameTypesResponse(List<Item> games, int dailyGameBugCap, Long dailyGameBugEarned) {

  static GameTypesResponse from(EconomyGameService.GameTypesView value) {
    return new GameTypesResponse(
        value.games().stream().map(Item::from).toList(),
        value.dailyGameBugCap(),
        value.dailyGameBugEarned());
  }

  public record Item(
      String gameType,
      String nameZh,
      String nameEn,
      String description,
      String icon,
      String status,
      String bugFormula,
      int dailyPlayLimit,
      Map<String, Object> uiMeta) {

    static Item from(EconomyGameService.GameTypeView value) {
      return new Item(
          value.gameType(),
          value.nameZh(),
          value.nameEn(),
          value.description(),
          value.icon(),
          value.status(),
          value.bugFormula(),
          value.dailyPlayLimit(),
          value.uiMeta());
    }
  }
}

