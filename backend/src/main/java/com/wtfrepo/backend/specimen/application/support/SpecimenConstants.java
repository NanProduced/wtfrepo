package com.wtfrepo.backend.specimen.application.support;

/**
 * Centralized specimen constants to avoid magic literals across API and service layers.
 */
public final class SpecimenConstants {

  private SpecimenConstants() {}

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Claim {

    public static final String ROLES = "roles";

    private Claim() {}
  }

  public static final class Role {

    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";

    private Role() {}
  }

  public static final class Message {

    public static final String INVALID_IDEMPOTENCY_KEY = "invalid_idempotency_key";
    public static final String IDEMPOTENCY_CONFLICT = "idempotency_conflict";
    public static final String INVALID_STATUS_TRANSITION = "invalid_status_transition";
    public static final String SPECIMEN_NOT_FOUND = "specimen_not_found";
    public static final String INVALID_ACTION = "invalid_action";
    public static final String INVALID_SORT = "invalid_sort";
    public static final String INVALID_CURSOR = "invalid_cursor";
    public static final String WATCHLIST_LIMIT_EXCEEDED = "watchlist_limit_exceeded";
    public static final String INVALID_TAG = "invalid_tag";
    public static final String INVALID_EXCERPT = "invalid_excerpt";
    public static final String INVALID_COMMENTARY = "invalid_commentary";
    public static final String INVALID_IDENTITY_BINDING = "invalid_identity_binding";
    public static final String FORBIDDEN_ADMIN = "forbidden_admin";

    private Message() {}
  }
}
