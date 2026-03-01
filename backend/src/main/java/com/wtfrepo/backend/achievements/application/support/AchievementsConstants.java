package com.wtfrepo.backend.achievements.application.support;

/** Centralized constants for M08 achievements APIs. */
public final class AchievementsConstants {

  private AchievementsConstants() {}

  public static final class Message {

    public static final String AUTH_REQUIRED = "auth_required";
    public static final String INVALID_STATUS = "invalid_status";
    public static final String INVALID_CURSOR = "invalid_cursor";
    public static final String ACH_NOT_FOUND = "ach_not_found";
    public static final String SECRET_HIDDEN_NAME = "???";
    public static final String SECRET_HIDDEN_DESCRIPTION = "隐藏成就，等待发现...";

    private Message() {}
  }
}
