package com.wtfrepo.backend.narrator.application.support;

/** Centralized constants for narrator/ticker APIs and outbox integration. */
public final class NarratorConstants {

  private NarratorConstants() {}

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Message {

    public static final String AUTH_REQUIRED = "auth_required";
    public static final String INVALID_MODE = "invalid_mode";
    public static final String INVALID_TONE = "invalid_tone";
    public static final String INVALID_PAYLOAD = "invalid_payload";
    public static final String INVALID_CURSOR = "invalid_cursor";
    public static final String INVALID_IDEMPOTENCY_KEY = "invalid_idempotency_key";

    private Message() {}
  }

  public static final class Outbox {

    public static final String AGGREGATE_TYPE = "NARRATOR_PREFERENCE";
    public static final String AGGREGATE_TYPE_TICKER = "NARRATOR_TICKER";
    public static final String EVENT_PREFERENCE_CHANGED = "NarratorPreferenceChangedEvent";
    public static final String EVENT_TICKER_ITEM_CLICKED = "TickerItemClickedEvent";

    private Outbox() {}
  }
}
