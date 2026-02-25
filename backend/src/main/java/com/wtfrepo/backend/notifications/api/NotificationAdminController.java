package com.wtfrepo.backend.notifications.api;

import com.wtfrepo.backend.admin.api.AdminApiSupport;
import com.wtfrepo.backend.notifications.application.NotificationBroadcastAdminService;
import com.wtfrepo.backend.notifications.application.NotificationBroadcastAdminService.BroadcastRecord;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal M07 -> M06 admin endpoints for broadcast delivery. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Admin: Notifications", description = "Internal admin notifications endpoints.")
public class NotificationAdminController {

  private final NotificationBroadcastAdminService broadcastAdminService;

  public NotificationAdminController(NotificationBroadcastAdminService broadcastAdminService) {
    this.broadcastAdminService = broadcastAdminService;
  }

  @PostMapping("/broadcast")
  @Operation(summary = "Create system broadcast (internal)")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Broadcast created")})
  public ResponseEntity<NotificationBroadcastResponse> createBroadcast(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody NotificationBroadcastRequest request) {
    var principal = AdminApiSupport.requireAdminPrincipal(jwt);
    BroadcastRecord record =
        broadcastAdminService.create(
            principal.userId(), request.title(), request.body(), request.targetUrl());
    return ResponseEntity.ok(NotificationBroadcastResponse.from(record));
  }

  public record NotificationBroadcastRequest(
      @NotBlank @Size(max = 120) String title,
      @Size(max = 5000) String body,
      @Size(max = 500) String targetUrl) {}

  public record NotificationBroadcastResponse(
      String broadcastUid,
      String title,
      String body,
      String targetUrl,
      String status,
      int totalRecipients,
      int deliveredCount,
      String createdBy,
      java.time.Instant createdAt,
      java.time.Instant completedAt) {

    static NotificationBroadcastResponse from(BroadcastRecord record) {
      if (record == null) {
        return null;
      }
      return new NotificationBroadcastResponse(
          record.broadcastUid(),
          record.title(),
          record.body(),
          record.targetUrl(),
          record.status(),
          record.totalRecipients(),
          record.deliveredCount(),
          record.createdBy(),
          record.createdAt(),
          record.completedAt());
    }
  }
}
