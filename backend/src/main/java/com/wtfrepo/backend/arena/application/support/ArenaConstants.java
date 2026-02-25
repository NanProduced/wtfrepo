package com.wtfrepo.backend.arena.application.support;

/** Centralized arena constants to avoid duplicated literals and magic strings. */
public final class ArenaConstants {

  private ArenaConstants() {}

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Battle {

    public static final String ID_PREFIX = "bat_";

    private Battle() {}
  }

  public static final class RateLimit {

    public static final String DUEL_ANON_KEY_PREFIX = "arena:rate:duel:anon:";
    public static final String DUEL_AUTH_USER_KEY_PREFIX = "arena:rate:duel:auth:user:";
    public static final String DUEL_AUTH_IP_KEY_PREFIX = "arena:rate:duel:auth:ip:";

    private RateLimit() {}
  }

  public static final class Economy {

    public static final String REF_TYPE_BATTLE = "BATTLE";

    private Economy() {}
  }

  public static final class Message {

    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String FORBIDDEN_ADMIN = "Admin role required";
    public static final String VOTE_INVALID_WINNER = "winner must be LEFT, RIGHT, or BOTH_BAD";
    public static final String BATTLE_NOT_FOUND = "Battle id signature is invalid";
    public static final String BATTLE_EXPIRED = "Battle id expired";
    public static final String VOTE_DUPLICATE = "Duplicate vote detected";
    public static final String INSUFFICIENT_BUG = "Insufficient bug balance";
    public static final String SETTLEMENT_IN_PROGRESS =
        "Settlement in progress, vote is temporarily unavailable";
    public static final String ARENA_NO_MATCH = "No arena match available for current filters";
    public static final String ARENA_POOL_EMPTY = "Active arena pool has fewer than two specimens";
    public static final String TOO_MANY_DUEL_REQUESTS = "Too many duel requests";

    private Message() {}
  }
}
