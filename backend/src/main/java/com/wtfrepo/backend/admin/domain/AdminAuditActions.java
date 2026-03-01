package com.wtfrepo.backend.admin.domain;

/** Audit action names emitted by admin platform workflows. */
public final class AdminAuditActions {

  private AdminAuditActions() {}

  public static final String ADMIN_BOOTSTRAP = "ADMIN_BOOTSTRAP";
  public static final String ADMIN_LOGIN = "ADMIN_LOGIN";
  public static final String ADMIN_LOGOUT = "ADMIN_LOGOUT";
  public static final String MANAGER_ADD = "MANAGER_ADD";
  public static final String MANAGER_REVOKE = "MANAGER_REVOKE";
  public static final String COMMENT_BLOCK = "COMMENT_BLOCK";
  public static final String COMMENT_UNBLOCK = "COMMENT_UNBLOCK";
  public static final String COMMENT_DELETE = "COMMENT_DELETE";
  public static final String COMMENT_APPROVE = "COMMENT_APPROVE";
  public static final String TICKET_REVIEW = "TICKET_REVIEW";
  public static final String ALERT_ACK = "ALERT_ACK";
  public static final String USER_BAN = "USER_BAN";
  public static final String USER_UNBAN = "USER_UNBAN";
  public static final String BROADCAST_SEND = "BROADCAST_SEND";
  public static final String ECONOMY_GRANT = "ECONOMY_GRANT";
  public static final String ECONOMY_REVOKE = "ECONOMY_REVOKE";
  public static final String BETTING_FORCE_SETTLE = "BETTING_FORCE_SETTLE";
  public static final String BETTING_HOUSE_CONFIG_UPDATE = "BETTING_HOUSE_CONFIG_UPDATE";
}
