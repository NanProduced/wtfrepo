package com.wtfrepo.backend.arena.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ArenaTradingDayResolverTest {

  @Test
  void shouldReturnPreviousDayBeforeMarketOpen() {
    LocalDate tradingDay =
        ArenaTradingDayResolver.currentTradingDay(Instant.parse("2026-02-12T00:02:00Z"));

    assertThat(tradingDay).isEqualTo(LocalDate.parse("2026-02-11"));
  }

  @Test
  void shouldReturnCurrentDayAfterMarketOpen() {
    LocalDate tradingDay =
        ArenaTradingDayResolver.currentTradingDay(Instant.parse("2026-02-12T00:06:00Z"));

    assertThat(tradingDay).isEqualTo(LocalDate.parse("2026-02-12"));
  }
}
