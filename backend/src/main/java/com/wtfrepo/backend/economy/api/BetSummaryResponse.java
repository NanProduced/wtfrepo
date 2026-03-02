package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.BettingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BetSummaryResponse(
    String specimenId,
    LocalDate date,
    long poolUp,
    long poolFlat,
    long poolDown,
    long houseUp,
    long houseFlat,
    long houseDown,
    BigDecimal oddsUp,
    BigDecimal oddsFlat,
    BigDecimal oddsDown,
    BigDecimal rakeRate,
    Instant betCutoffAt,
    String poolStatus,
    long totalBettors,
    boolean houseActive,
    String ipoStatus,
    int currentElo,
    int eloOpenToday,
    int deltaRSoFar,
    int correctionToday,
    int moonDoomThreshold,
    boolean canBet,
    String betBlockReasonCode,
    boolean hasVotedForSpecimenToday) {

  static BetSummaryResponse from(BettingService.BetSummaryView value) {
    return new BetSummaryResponse(
        value.specimenId(),
        value.date(),
        value.poolUp(),
        value.poolFlat(),
        value.poolDown(),
        value.houseUp(),
        value.houseFlat(),
        value.houseDown(),
        value.oddsUp(),
        value.oddsFlat(),
        value.oddsDown(),
        value.rakeRate(),
        value.betCutoffAt(),
        value.poolStatus(),
        value.totalBettors(),
        value.houseActive(),
        value.ipoStatus(),
        value.currentElo(),
        value.eloOpenToday(),
        value.deltaRSoFar(),
        value.correctionToday(),
        value.moonDoomThreshold(),
        value.canBet(),
        value.betBlockReasonCode(),
        value.hasVotedForSpecimenToday());
  }
}
