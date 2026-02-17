package com.wtfrepo.backend.arena.application.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Utility for resolving Arena/Betting trading day boundaries in UTC. */
public final class ArenaTradingDayResolver {

  private static final LocalTime MARKET_OPEN_UTC = LocalTime.of(0, 5);

  private ArenaTradingDayResolver() {}

  public static LocalDate currentTradingDay(Instant now) {
    ZonedDateTime utcDateTime = now.atZone(ZoneOffset.UTC);
    if (utcDateTime.toLocalTime().isBefore(MARKET_OPEN_UTC)) {
      return utcDateTime.toLocalDate().minusDays(1);
    }
    return utcDateTime.toLocalDate();
  }

  public static LocalDate settlementTradingDay(Instant now) {
    return currentTradingDay(now);
  }
}
