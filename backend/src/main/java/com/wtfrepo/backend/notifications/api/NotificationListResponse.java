package com.wtfrepo.backend.notifications.api;

import com.wtfrepo.backend.notifications.application.NotificationService;
import java.time.Instant;
import java.util.List;

public record NotificationListResponse(
    List<NotificationListItemResponse> items, String nextCursor, boolean hasMore) {

  static NotificationListResponse from(NotificationService.ListResult result) {
    List<NotificationListItemResponse> items =
        result.items().stream().map(NotificationListItemResponse::from).toList();
    return new NotificationListResponse(items, result.nextCursor(), result.hasMore());
  }

  public record NotificationListItemResponse(
      String notificationUid,
      String type,
      String title,
      String body,
      String actorNickname,
      String actorAvatarUrl,
      int aggregateCount,
      String targetUrl,
      String fallbackUrl,
      String status,
      Instant createdAt) {

    static NotificationListItemResponse from(NotificationService.ListItem item) {
      return new NotificationListItemResponse(
          item.notificationUid(),
          item.type(),
          item.title(),
          item.body(),
          item.actorNickname(),
          item.actorAvatarUrl(),
          item.aggregateCount(),
          item.targetUrl(),
          item.fallbackUrl(),
          item.status(),
          item.createdAt());
    }
  }
}
