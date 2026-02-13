package com.wtfrepo.backend.arena.domain;

/** Winner side in arena vote. */
public enum ArenaVoteWinner {
  LEFT,
  RIGHT,
  BOTH_BAD;

  public static ArenaVoteWinner parse(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return ArenaVoteWinner.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}

