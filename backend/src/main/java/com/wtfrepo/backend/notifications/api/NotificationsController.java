package com.wtfrepo.backend.notifications.api;

import com.wtfrepo.backend.notifications.application.NotificationService;
import com.wtfrepo.backend.notifications.application.NotificationService.ListResult;
import com.wtfrepo.backend.notifications.application.NotificationService.MarkAllReadResult;
import com.wtfrepo.backend.notifications.application.NotificationService.MarkReadResult;
import com.wtfrepo.backend.notifications.application.NotificationService.UnreadCountResult;
import com.wtfrepo.backend.notifications.application.support.NotificationsConstants;
import com.wtfrepo.backend.notifications.application.support.NotificationsExceptions;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public notifications API for M06 pager experience. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class NotificationsController {

  private final NotificationService notificationService;

  public NotificationsController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping("/notifications")
  public ResponseEntity<NotificationListResponse> list(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "type", required = false) String type,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "limit", required = false) @Min(1) @Max(50) Integer limit) {
    String userId = requireUserId(jwt);
    ListResult result =
        notificationService.list(
            userId, new NotificationService.ListQuery(status, type, cursor, limit));
    return ResponseEntity.ok(NotificationListResponse.from(result));
  }

  @GetMapping("/notifications/unread-count")
  public ResponseEntity<NotificationUnreadCountResponse> unreadCount(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    UnreadCountResult result = notificationService.unreadCount(userId);
    return ResponseEntity.ok(new NotificationUnreadCountResponse(result.unreadCount()));
  }

  @PatchMapping("/notifications/{notificationUid}/read")
  public ResponseEntity<NotificationReadResponse> markRead(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(name = NotificationsConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String notificationUid) {
    String userId = requireUserId(jwt);
    MarkReadResult result = notificationService.markRead(userId, notificationUid);
    return ResponseEntity.ok(NotificationReadResponse.from(result));
  }

  @PatchMapping("/notifications/read-all")
  public ResponseEntity<NotificationReadAllResponse> markAllRead(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(name = NotificationsConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    MarkAllReadResult result = notificationService.markAllRead(userId);
    return ResponseEntity.ok(new NotificationReadAllResponse(result.updatedCount()));
  }

  private String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw NotificationsExceptions.unauthorized(
          NotificationsConstants.Message.AUTH_REQUIRED);
    }
    return jwt.getSubject().trim();
  }
}
