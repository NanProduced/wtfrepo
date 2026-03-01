package com.wtfrepo.backend.admin.application.support;

/** Centralized admin constants to avoid duplicated literals and magic strings. */
public final class AdminConstants {

  private AdminConstants() {}

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

    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String FORBIDDEN_ADMIN = "Admin role required";
    public static final String ADMIN_ROLE_REQUIRED = "admin_role_required";
    public static final String BOOTSTRAP_ALREADY_DONE = "bootstrap_already_done";
    public static final String BOOTSTRAP_EMAIL_MISMATCH = "bootstrap_email_mismatch";
    public static final String INVALID_EMAIL = "invalid_email";
    public static final String INVALID_IDEMPOTENCY_KEY = "invalid_idempotency_key";
    public static final String IDEMPOTENCY_CONFLICT = "idempotency_conflict";
    public static final String MANAGER_ALREADY_ADMIN = "manager_already_admin";
    public static final String MANAGER_NOT_FOUND = "manager_not_found";
    public static final String SAFETY_TICKET_NOT_FOUND = "safety_ticket_not_found";
    public static final String INVALID_SAFETY_TICKET_STATUS = "invalid_safety_ticket_status";
    public static final String INVALID_SAFETY_TICKET_TRANSITION = "invalid_safety_ticket_transition";
    public static final String ALERT_NOT_FOUND = "alert_not_found";
    public static final String INVALID_BAN_TYPE = "invalid_ban_type";
    public static final String INVALID_BAN_REQUEST = "invalid_ban_request";
    public static final String INVALID_BROADCAST_REQUEST = "invalid_broadcast_request";
    public static final String INVALID_ECONOMY_GRANT_REQUEST = "invalid_economy_grant_request";
    public static final String INVALID_BETTING_FORCE_SETTLE_REQUEST = "invalid_betting_force_settle_request";
    public static final String INVALID_BETTING_HOUSE_CONFIG_REQUEST = "invalid_betting_house_config_request";
    public static final String INSUFFICIENT_BUG = "insufficient_bug";
    public static final String BAN_NOT_FOUND = "ban_not_found";
    public static final String USER_NOT_FOUND = "user_not_found";

    private Message() {}
  }
}
