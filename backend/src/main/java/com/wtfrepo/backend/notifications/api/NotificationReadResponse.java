package com.wtfrepo.backend.notifications.api;

import com.wtfrepo.backend.notifications.application.NotificationService;
import java.time.Instant;

public record NotificationReadResponse(String notificationUid, String status, Instant readAt) {

  static NotificationReadResponse from(NotificationService.MarkReadResult result) {
    return new NotificationReadResponse(
        result.notificationUid(),
        result.status() != null ? result.status().name() : null,
        result.readAt());
  }
}
