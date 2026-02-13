package com.wtfrepo.backend.arena.domain;

/**
 * Elo calculation helper.
 *
 * <p>Contract-aligned behavior:
 *
 * <ul>
 *   <li>Dynamic K by matches played: 64 (0-9), 32 (10-29), 16 (>=30).
 *   <li>BOTH_BAD penalizes both sides by -K/8.
 * </ul>
 */
public final class ArenaEloCalculator {

  private ArenaEloCalculator() {}

  public static Result compute(Input input) {
    int leftK = kFactor(input.leftMatchesPlayedBefore());
    int rightK = kFactor(input.rightMatchesPlayedBefore());

    int leftDelta;
    int rightDelta;
    if (input.winner() == ArenaVoteWinner.BOTH_BAD) {
      leftDelta = -leftK / 8;
      rightDelta = -rightK / 8;
    } else {
      double expectedLeft = expectedScore(input.leftEloBefore(), input.rightEloBefore());
      double expectedRight = 1.0D - expectedLeft;
      int leftScore = input.winner() == ArenaVoteWinner.LEFT ? 1 : 0;
      int rightScore = 1 - leftScore;
      leftDelta = (int) Math.round(leftK * (leftScore - expectedLeft));
      rightDelta = (int) Math.round(rightK * (rightScore - expectedRight));
    }

    long leftMatchesPlayedAfter = input.leftMatchesPlayedBefore() + 1;
    long rightMatchesPlayedAfter = input.rightMatchesPlayedBefore() + 1;

    int leftEloAfter = input.leftEloBefore() + leftDelta;
    int rightEloAfter = input.rightEloBefore() + rightDelta;

    return new Result(
        leftDelta,
        rightDelta,
        leftEloAfter,
        rightEloAfter,
        leftK,
        rightK,
        ArenaRatingPhase.fromMatchesPlayed(leftMatchesPlayedAfter),
        ArenaRatingPhase.fromMatchesPlayed(rightMatchesPlayedAfter));
  }

  private static int kFactor(long matchesPlayedBefore) {
    if (matchesPlayedBefore < 10) {
      return 64;
    }
    if (matchesPlayedBefore < 30) {
      return 32;
    }
    return 16;
  }

  private static double expectedScore(int ratingA, int ratingB) {
    double exponent = (ratingB - ratingA) / 400.0D;
    return 1.0D / (1.0D + Math.pow(10.0D, exponent));
  }

  public record Input(
      ArenaVoteWinner winner,
      int leftEloBefore,
      int rightEloBefore,
      long leftMatchesPlayedBefore,
      long rightMatchesPlayedBefore) {}

  public record Result(
      int leftDelta,
      int rightDelta,
      int leftEloAfter,
      int rightEloAfter,
      int leftKFactor,
      int rightKFactor,
      ArenaRatingPhase leftPhaseAfter,
      ArenaRatingPhase rightPhaseAfter) {}
}

