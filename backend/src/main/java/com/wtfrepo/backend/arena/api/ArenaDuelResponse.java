package com.wtfrepo.backend.arena.api;

import com.wtfrepo.backend.arena.application.ArenaDuelService;
import java.util.List;

public record ArenaDuelResponse(
    String battleId,
    DuelSpecimen left,
    DuelSpecimen right,
    DuelMatchMeta matchMeta,
    boolean shouldResetExcludeSet,
    DuelWallet wallet) {

  static ArenaDuelResponse from(ArenaDuelService.DuelResult result) {
    return new ArenaDuelResponse(
        result.battleId(),
        toSpecimen(result.left()),
        toSpecimen(result.right()),
        new DuelMatchMeta(
            result.matchMeta().matchType(),
            result.matchMeta().matchProfileVersion(),
            result.matchMeta().isIpoMatch()),
        result.shouldResetExcludeSet(),
        result.wallet() == null ? null : new DuelWallet(result.wallet().balance(), result.wallet().voteCost()));
  }

  private static DuelSpecimen toSpecimen(ArenaDuelService.DuelSpecimen specimen) {
    return new DuelSpecimen(
        specimen.specimenId(),
        specimen.title(),
        specimen.tagline(),
        specimen.species(),
        specimen.diagnosisTags(),
        specimen.elo(),
        specimen.matchesPlayed(),
        specimen.ipoStatus(),
        specimen.thumbnailUrl());
  }

  public record DuelSpecimen(
      String specimenId,
      String title,
      String tagline,
      String species,
      List<String> diagnosisTags,
      int elo,
      long matchesPlayed,
      String ipoStatus,
      String thumbnailUrl) {}

  public record DuelMatchMeta(String matchType, String matchProfileVersion, boolean isIpoMatch) {}

  public record DuelWallet(long balance, int voteCost) {}
}