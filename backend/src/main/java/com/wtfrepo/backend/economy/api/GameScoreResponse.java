package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyGameService;

/** Score submission settlement payload for mini-game economy endpoint. */
public record GameScoreResponse(
    String sessionId,
    String gameType,
    int score,
    int bugEarned,
    long balanceAfter,
    String validationStatus,
    String validationReason,
    int dailyGameCount,
    int dailyGameBugEarned,
    int dailyGameBugCap) {

  static GameScoreResponse from(EconomyGameService.SubmitScoreResult value) {
    return new GameScoreResponse(
        value.sessionId(),
        value.gameType(),
        value.score(),
        value.bugEarned(),
        value.balanceAfter(),
        value.validationStatus(),
        value.validationReason(),
        value.dailyGameCount(),
        value.dailyGameBugEarned(),
        value.dailyGameBugCap());
  }
}

