package com.wtfrepo.backend.arena.api;

import com.wtfrepo.backend.arena.application.ArenaVoteService;

public record ArenaVoteResponse(
    String battleId,
    String winner,
    int leftDelta,
    int rightDelta,
    int leftEloAfter,
    int rightEloAfter,
    String leftPhase,
    String rightPhase,
    int bugCost,
    long walletBalanceAfter) {

  static ArenaVoteResponse from(ArenaVoteService.VoteResult result) {
    return new ArenaVoteResponse(
        result.battleId(),
        result.winner(),
        result.leftDelta(),
        result.rightDelta(),
        result.leftEloAfter(),
        result.rightEloAfter(),
        result.leftPhase(),
        result.rightPhase(),
        result.bugCost(),
        result.walletBalanceAfter());
  }
}
