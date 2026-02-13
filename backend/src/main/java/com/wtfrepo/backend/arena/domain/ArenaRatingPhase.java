package com.wtfrepo.backend.arena.domain;

/** Rating phase derived from total matches played. */
public enum ArenaRatingPhase {
  CALIBRATING,
  GROWING,
  STABLE;

  public static ArenaRatingPhase fromMatchesPlayed(long matchesPlayed) {
    if (matchesPlayed < 10) {
      return CALIBRATING;
    }
    if (matchesPlayed < 30) {
      return GROWING;
    }
    return STABLE;
  }
}

