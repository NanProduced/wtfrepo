package com.wtfrepo.backend.admin.domain;

/** Target type constants for admin audit logs. */
public final class AdminAuditTargetType {

  private AdminAuditTargetType() {}

  public static final String USER = "USER";
  public static final String PLATFORM = "PLATFORM";
  public static final String COMMENT = "COMMENT";
  public static final String SAFETY_TICKET = "SAFETY_TICKET";
  public static final String ALERT = "ALERT";
}
