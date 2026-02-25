package com.wtfrepo.backend.notifications.application.support;

public final class NotificationsConstants {

  private NotificationsConstants() {}

  public static final class Header {
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Message {
    public static final String AUTH_REQUIRED = "Authentication required.";
    public static final String NOTIFICATION_NOT_FOUND = "Notification not found.";
    public static final String NOTIFICATION_FORBIDDEN = "Notification access denied.";
    public static final String INVALID_STATUS = "Invalid notification status.";
    public static final String INVALID_TYPE = "Invalid notification type.";
    public static final String INVALID_CURSOR = "Invalid cursor.";
    public static final String INVALID_CHANNEL = "Invalid channel.";

    private Message() {}
  }
}
