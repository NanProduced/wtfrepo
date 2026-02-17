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
    public static final String IDEMPOTENCY_CONFLICT =
        "Idempotency key already exists with different request payload";
    public static final String INSUFFICIENT_BUG = "Insufficient bug balance";
    public static final String SPECIMEN_NOT_FOUND = "Specimen not found";

    private Message() {}
  }

  public static final class RefType {

    public static final String BET = "BET";
    public static final String HOUSE_STAKE = "HOUSE_STAKE";

    private RefType() {}
  }

  public static final class User {

    public static final String HOUSE = "HOUSE";

    private User() {}
  }
}
