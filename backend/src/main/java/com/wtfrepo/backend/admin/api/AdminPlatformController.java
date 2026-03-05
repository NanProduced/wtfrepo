package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.AdminPlatformService;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuthResult;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminManagerRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketRecord;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/platform")
@Tag(name = "Admin: Platform", description = "M07 admin platform entry endpoints.")
public class AdminPlatformController {

  private final AdminPlatformService adminPlatformService;

  public AdminPlatformController(AdminPlatformService adminPlatformService) {
    this.adminPlatformService = adminPlatformService;
  }

  @GetMapping("/me")
  @Operation(summary = "Get admin profile", description = "Returns current admin user context.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Admin profile")})
  public ResponseEntity<AdminMeResponse> me(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    return ResponseEntity.ok(AdminMeResponse.from(principal));
  }

  @PostMapping("/bootstrap")
  @Operation(summary = "Bootstrap admin")
  public ResponseEntity<AdminTokenResponse> bootstrap(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminBootstrapRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireUserPrincipal(jwt);
    AdminAuthResult result =
        adminPlatformService.bootstrap(
            requestId,
            idempotencyKey,
            principal.userId(),
            request.email(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminTokenResponse.from(result));
  }

  @PostMapping("/login")
  @Operation(summary = "Admin login")
  public ResponseEntity<AdminTokenResponse> login(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireUserPrincipal(jwt);
    AdminAuthResult result =
        adminPlatformService.login(
            requestId,
            idempotencyKey,
            principal.userId(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminTokenResponse.from(result));
  }

  @PostMapping("/oauth/authorize")
  @Operation(summary = "Issue admin OAuth authorization code")
  public ResponseEntity<AdminOAuthAuthorizeResponse> authorizeOAuth(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminOAuthAuthorizeRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireUserPrincipal(jwt);
    AdminPlatformService.OAuthAuthorizeResult result =
        adminPlatformService.issueOAuthAuthorizationCode(
            requestId,
            idempotencyKey,
            principal.userId(),
            request.redirectUri(),
            request.state(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminOAuthAuthorizeResponse.from(result));
  }

  @PostMapping("/oauth/token")
  @Operation(summary = "Exchange OAuth authorization code to admin token")
  public ResponseEntity<AdminTokenResponse> exchangeOAuthToken(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Valid @RequestBody AdminOAuthTokenRequest request,
      HttpServletRequest servletRequest) {
    AdminAuthResult result =
        adminPlatformService.exchangeOAuthAuthorizationCode(
            requestId,
            idempotencyKey,
            request.code(),
            request.redirectUri(),
            request.state(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminTokenResponse.from(result));
  }

  @PostMapping("/logout")
  @Operation(summary = "Admin logout (token revoke)")
  public ResponseEntity<AdminLogoutResponse> logout(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminPlatformService.AdminLogoutResult result =
        adminPlatformService.logout(
            principal,
            requestId,
            idempotencyKey,
            jwt != null ? jwt.getId() : null,
            jwt != null ? jwt.getExpiresAt() : null,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminLogoutResponse.from(result));
  }

  @GetMapping("/managers")
  @Operation(summary = "List managers")
  public ResponseEntity<AdminManagersResponse> listManagers(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    List<AdminManagerResponse> managers =
        adminPlatformService.listManagers(principal).stream()
            .map(AdminManagerResponse::from)
            .toList();
    return ResponseEntity.ok(new AdminManagersResponse(managers));
  }

  @PostMapping("/managers")
  @Operation(summary = "Create manager")
  public ResponseEntity<AdminManagerResponse> createManager(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminManagerCreateRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminManagerRecord record =
        adminPlatformService.createManager(
            principal,
            requestId,
            idempotencyKey,
            request.userId(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminManagerResponse.from(record));
  }

  @DeleteMapping("/managers/{userId}")
  @Operation(summary = "Revoke manager")
  public ResponseEntity<AdminManagerResponse> deleteManager(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Target user id.", required = true) @PathVariable String userId,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminManagerRecord record =
        adminPlatformService.revokeManager(
            principal,
            requestId,
            idempotencyKey,
            userId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminManagerResponse.from(record));
  }

  @GetMapping("/safety-tickets")
  @Operation(summary = "List safety tickets")
  public ResponseEntity<AdminSafetyTicketPageResponse> listSafetyTickets(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize,
      @RequestParam(name = "status", required = false) String status) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminSafetyTicketPage tickets =
        adminPlatformService.listSafetyTickets(principal, page, pageSize, status);
    return ResponseEntity.ok(AdminSafetyTicketPageResponse.from(tickets));
  }

  @PatchMapping("/safety-tickets/{ticketId}")
  @Operation(summary = "Update safety ticket")
  public ResponseEntity<AdminSafetyTicketResponse> updateSafetyTicket(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Safety ticket id.", required = true) @PathVariable String ticketId,
      @Valid @RequestBody AdminSafetyTicketUpdateRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminSafetyTicketRecord record =
        adminPlatformService.updateSafetyTicket(
            principal,
            requestId,
            idempotencyKey,
            ticketId,
            request.status(),
            request.resolution(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminSafetyTicketResponse.from(record));
  }

  @PostMapping("/broadcasts")
  @Operation(summary = "Create broadcast")
  public ResponseEntity<AdminBroadcastResponse> createBroadcast(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminBroadcastCreateRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    var record =
        adminPlatformService.createBroadcast(
            principal,
            requestId,
            idempotencyKey,
            request.title(),
            request.body(),
            request.targetUrl(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminBroadcastResponse.from(record));
  }

  @GetMapping("/broadcasts")
  @Operation(summary = "List broadcasts")
  public ResponseEntity<AdminBroadcastPageResponse> listBroadcasts(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    var pageResult = adminPlatformService.listBroadcasts(principal, page, pageSize);
    return ResponseEntity.ok(AdminBroadcastPageResponse.from(pageResult));
  }

  @GetMapping("/audit-logs")
  @Operation(summary = "List audit logs")
  public ResponseEntity<AdminAuditLogPageResponse> listAuditLogs(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize,
      @RequestParam(name = "operatorId", required = false) String operatorId,
      @RequestParam(name = "action", required = false) String action,
      @RequestParam(name = "targetType", required = false) String targetType,
      @RequestParam(name = "targetId", required = false) String targetId) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminAuditLogPage logPage =
        adminPlatformService.listAuditLogs(principal, page, pageSize, operatorId, action, targetType, targetId);
    return ResponseEntity.ok(AdminAuditLogPageResponse.from(logPage));
  }

  @GetMapping("/alerts")
  @Operation(summary = "List alerts")
  public ResponseEntity<AdminAlertPageResponse> listAlerts(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize,
      @RequestParam(name = "acknowledged", required = false) Boolean acknowledged) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminAlertPage alerts = adminPlatformService.listAlerts(principal, page, pageSize, acknowledged);
    return ResponseEntity.ok(AdminAlertPageResponse.from(alerts));
  }

  @PatchMapping("/alerts/{alertId}/acknowledge")
  @Operation(summary = "Acknowledge alert")
  public ResponseEntity<AdminAlertResponse> acknowledgeAlert(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Alert id.", required = true) @PathVariable String alertId,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminAlertRecord record =
        adminPlatformService.acknowledgeAlert(
            principal,
            requestId,
            idempotencyKey,
            alertId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminAlertResponse.from(record));
  }

  private ResponseEntity<AdminPlaceholderResponse> notImplemented(String path) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(
            new AdminPlaceholderResponse(
                "ADMIN_CONTRACT_PENDING",
                path + " is reserved for M07 admin contract and is not implemented yet."));
  }

  private String resolveClientIp(HttpServletRequest servletRequest) {
    if (servletRequest == null) {
      return null;
    }
    String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String firstHop =
          Arrays.stream(forwardedFor.split(","))
              .map(String::trim)
              .filter(StringUtils::hasText)
              .findFirst()
              .orElse("");
      if (StringUtils.hasText(firstHop)) {
        return firstHop;
      }
    }

    String realIp = servletRequest.getHeader("X-Real-IP");
    if (StringUtils.hasText(realIp)) {
      return realIp.trim();
    }
    return servletRequest.getRemoteAddr();
  }

  private String resolveUserAgent(HttpServletRequest servletRequest) {
    if (servletRequest == null) {
      return null;
    }
    String userAgent = servletRequest.getHeader("User-Agent");
    return StringUtils.hasText(userAgent) ? userAgent.trim() : null;
  }

  public record AdminMeResponse(String userId, String username, List<String> roles) {

    static AdminMeResponse from(AdminPrincipal principal) {
      return new AdminMeResponse(principal.userId(), principal.username(), principal.roles());
    }
  }

  public record AdminBootstrapRequest(@NotBlank String email) {}

  public record AdminOAuthAuthorizeRequest(@NotBlank String redirectUri, @NotBlank String state) {}

  public record AdminOAuthTokenRequest(
      @NotBlank String code, @NotBlank String redirectUri, @NotBlank String state) {}

  public record AdminManagerCreateRequest(@NotBlank String userId) {}

  public record AdminBroadcastCreateRequest(
      @NotBlank @Size(max = 120) String title,
      @Size(max = 5000) String body,
      @Size(max = 500) String targetUrl) {}

  public record AdminTokenResponse(
      String accessToken,
      String tokenType,
      long expiresIn,
      Instant expiresAt,
      AdminUserResponse user) {

    static AdminTokenResponse from(AdminAuthResult result) {
      return new AdminTokenResponse(
          result.issuedToken().accessToken(),
          "Bearer",
          result.issuedToken().expiresIn(),
          result.issuedToken().expiresAt(),
          new AdminUserResponse(result.userId(), result.username(), result.roles()));
    }
  }

  public record AdminOAuthAuthorizeResponse(
      String code,
      Instant expiresAt,
      String redirectUri,
      String state,
      AdminUserResponse user) {

    static AdminOAuthAuthorizeResponse from(AdminPlatformService.OAuthAuthorizeResult result) {
      return new AdminOAuthAuthorizeResponse(
          result.code(),
          result.expiresAt(),
          result.redirectUri(),
          result.state(),
          new AdminUserResponse(result.userId(), result.username(), result.roles()));
    }
  }

  public record AdminUserResponse(String userId, String username, List<String> roles) {}

  public record AdminManagerResponse(
      String userId,
      String username,
      String role,
      boolean active,
      String grantedBy,
      Instant grantedAt,
      String revokedBy,
      Instant revokedAt) {

    static AdminManagerResponse from(AdminManagerRecord record) {
      return new AdminManagerResponse(
          record.userId(),
          record.username(),
          record.role(),
          record.active(),
          record.grantedBy(),
          record.grantedAt(),
          record.revokedBy(),
          record.revokedAt());
    }
  }

  public record AdminManagersResponse(List<AdminManagerResponse> items) {}

  public record AdminLogoutResponse(boolean revoked, Instant expiresAt) {

    static AdminLogoutResponse from(AdminPlatformService.AdminLogoutResult result) {
      return new AdminLogoutResponse(result.revoked(), result.expiresAt());
    }
  }

  public record AdminSafetyTicketUpdateRequest(@NotBlank String status, String resolution) {}

  public record AdminBroadcastResponse(
      String broadcastUid,
      String title,
      String body,
      String targetUrl,
      String status,
      int totalRecipients,
      int deliveredCount,
      String createdBy,
      Instant createdAt,
      Instant completedAt) {

    static AdminBroadcastResponse from(
        com.wtfrepo.backend.notifications.application.NotificationBroadcastAdminService.BroadcastRecord record) {
      if (record == null) {
        return null;
      }
      return new AdminBroadcastResponse(
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

  public record AdminBroadcastPageResponse(
      List<AdminBroadcastResponse> items, AdminPaginationResponse pagination) {

    static AdminBroadcastPageResponse from(
        com.wtfrepo.backend.notifications.application.NotificationBroadcastAdminService.BroadcastPage page) {
      List<AdminBroadcastResponse> items =
          page.items().stream().map(AdminBroadcastResponse::from).toList();
      return new AdminBroadcastPageResponse(
          items, new AdminPaginationResponse(page.page(), page.pageSize(), page.total(), page.totalPages()));
    }
  }

  public record AdminSafetyTicketResponse(
      String id,
      String source,
      String status,
      String reporterId,
      String targetType,
      String targetId,
      String reason,
      String resolution,
      String resolvedBy,
      Instant resolvedAt,
      Instant createdAt,
      Instant updatedAt) {

    static AdminSafetyTicketResponse from(AdminSafetyTicketRecord record) {
      return new AdminSafetyTicketResponse(
          record.id(),
          record.source() != null ? record.source().name() : null,
          record.status() != null ? record.status().name() : null,
          record.reporterId(),
          record.targetType(),
          record.targetId(),
          record.reason(),
          record.resolution(),
          record.resolvedBy(),
          record.resolvedAt(),
          record.createdAt(),
          record.updatedAt());
    }
  }

  public record AdminSafetyTicketPageResponse(
      List<AdminSafetyTicketResponse> items,
      AdminPaginationResponse pagination) {

    static AdminSafetyTicketPageResponse from(AdminSafetyTicketPage page) {
      List<AdminSafetyTicketResponse> items =
          page.items().stream().map(AdminSafetyTicketResponse::from).toList();
      return new AdminSafetyTicketPageResponse(
          items,
          new AdminPaginationResponse(
              page.page(), page.pageSize(), page.total(), page.totalPages()));
    }
  }

  public record AdminAlertResponse(
      String id,
      String alertType,
      String severity,
      String targetType,
      String targetId,
      String message,
      List<String> emailSentTo,
      Instant emailSentAt,
      boolean acknowledged,
      String acknowledgedBy,
      Instant createdAt) {

    static AdminAlertResponse from(AdminAlertRecord record) {
      return new AdminAlertResponse(
          record.id(),
          record.alertType() != null ? record.alertType().name() : null,
          record.severity() != null ? record.severity().name() : null,
          record.targetType(),
          record.targetId(),
          record.message(),
          record.emailSentTo(),
          record.emailSentAt(),
          record.acknowledged(),
          record.acknowledgedBy(),
          record.createdAt());
    }
  }

  public record AdminAlertPageResponse(
      List<AdminAlertResponse> items,
      AdminPaginationResponse pagination) {

    static AdminAlertPageResponse from(AdminAlertPage page) {
      List<AdminAlertResponse> items =
          page.items().stream().map(AdminAlertResponse::from).toList();
      return new AdminAlertPageResponse(
          items,
          new AdminPaginationResponse(
              page.page(), page.pageSize(), page.total(), page.totalPages()));
    }
  }

  public record AdminAuditLogResponse(
      String id,
      String operatorId,
      String action,
      String targetType,
      String targetId,
      Object beforeSnapshot,
      Object afterSnapshot,
      Object metadata,
      String requestId,
      String ipAddress,
      String userAgent,
      Instant createdAt) {

    static AdminAuditLogResponse from(AdminAuditLogEntry entry) {
      return new AdminAuditLogResponse(
          entry.id(),
          entry.operatorId(),
          entry.action(),
          entry.targetType(),
          entry.targetId(),
          entry.beforeSnapshot(),
          entry.afterSnapshot(),
          entry.metadata(),
          entry.requestId(),
          entry.ipAddress(),
          entry.userAgent(),
          entry.createdAt());
    }
  }

  public record AdminAuditLogPageResponse(
      List<AdminAuditLogResponse> items,
      AdminPaginationResponse pagination) {

    static AdminAuditLogPageResponse from(AdminAuditLogPage page) {
      List<AdminAuditLogResponse> items =
          page.items().stream().map(AdminAuditLogResponse::from).toList();
      return new AdminAuditLogPageResponse(
          items,
          new AdminPaginationResponse(
              page.page(), page.pageSize(), page.total(), page.totalPages()));
    }
  }

  public record AdminPaginationResponse(int page, int pageSize, int total, int totalPages) {}

  private record AdminPlaceholderResponse(String code, String message) {}
}
