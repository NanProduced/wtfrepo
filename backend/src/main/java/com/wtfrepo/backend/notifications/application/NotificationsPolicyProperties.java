package com.wtfrepo.backend.notifications.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Property-backed M06 notifications policy snapshot. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationsPolicyProperties {

  private int listDefaultLimit = 20;
  private int listMaxLimit = 50;
  private int retentionDays = 90;
  private int aggregationWindowMinutes = 10;
  private int broadcastBatchSize = 500;
  private Duration unreadCacheTtl = Duration.ZERO;
  private String unreadKeyPrefix = "notify:unread:";
  private int sseHeartbeatSeconds = 30;
}
