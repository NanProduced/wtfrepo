package com.wtfrepo.backend.economy.application.support;

/** Centralized constants for betting APIs and write-path semantics. */
public final class BettingConstants {

  private BettingConstants() {}

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Message {

    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String BET_INVALID_DIRECTION =
        "direction must be UP, FLAT, or DOWN";
    public static final String BET_AMOUNT_TOO_LOW = "Bet amount is below minimum";
    public static final String BET_IPO_LOCKED = "Specimen is not open for betting";
    public static final String BET_CUTOFF_PASSED = "Bet cutoff time has passed";
    public static final String BET_POOL_NOT_AVAILABLE = "Bet pool is not available";
    public static final String BET_VOTE_REQUIRED =
        "Vote on this specimen in Arena before betting";
    public static final String IDEMPOTENCY_CONFLICT =
        "Idempotency key already exists with different request payload";
    public static final String INSUFFICIENT_BUG = "Insufficient bug balance";
    public static final String SPECIMEN_NOT_FOUND = "Specimen not found";

    private Message() {}
  }

  public static final class BetBlockReason {

    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String VOTE_REQUIRED = "VOTE_REQUIRED";
    public static final String IPO_LOCKED = "IPO_LOCKED";
    public static final String POOL_NOT_AVAILABLE = "POOL_NOT_AVAILABLE";
    public static final String POOL_NOT_OPEN = "POOL_NOT_OPEN";
    public static final String CUTOFF_PASSED = "CUTOFF_PASSED";

    private BetBlockReason() {}
  }

  public static final class RefType {

    public static final String BET = "BET";
    public static final String HOUSE_STAKE = "HOUSE_STAKE";
    public static final String SYSTEM = "SYSTEM";

    private RefType() {}
  }

  public static final class User {

    public static final String HOUSE = "HOUSE";

    private User() {}
  }
}
