package com.wtfrepo.backend.economy.application.support;

/** Centralized constants for economy wallet/game APIs. */
public final class EconomyConstants {

  private EconomyConstants() {}

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Message {

    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String INVALID_CURSOR = "Invalid cursor";
    public static final String INVALID_REASON = "Invalid ledger reason";
    public static final String GAME_INVALID_TYPE = "Game type is invalid or not active";
    public static final String GAME_INVALID_SCORE = "Game score payload is invalid";
    public static final String GAME_DAILY_LIMIT_EXCEEDED = "Daily play limit exceeded";
    public static final String GAME_DAILY_BUG_CAP_REACHED = "Daily game bug cap reached";

    private Message() {}
  }

  public static final class RefType {

    public static final String GAME = "GAME";
    public static final String ADMIN = "ADMIN";
    public static final String ACHIEVEMENT = "ACHIEVEMENT";

    private RefType() {}
  }

  /** Shared outbox constants for economy-owned domain events. */
  public static final class Outbox {

    public static final String AGGREGATE_TYPE_WALLET = "BUG_WALLET";
    public static final String AGGREGATE_TYPE_GAME_SESSION = "GAME_SESSION";

    public static final String EVENT_BUG_BALANCE_CHANGED = "BugBalanceChangedEvent";
    public static final String EVENT_DAILY_CLAIMED = "DailyClaimedEvent";
    public static final String EVENT_GAME_SESSION_COMPLETED = "GameSessionCompletedEvent";

    private Outbox() {}
  }
}
