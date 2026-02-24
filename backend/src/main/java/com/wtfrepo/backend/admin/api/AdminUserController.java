package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.AdminPlatformService;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanRecord;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: Users", description = "Admin user moderation endpoints.")
public class AdminUserController {

  private final AdminPlatformService adminPlatformService;

  public AdminUserController(AdminPlatformService adminPlatformService) {
    this.adminPlatformService = adminPlatformService;
  }

  @PostMapping("/{userId}/ban")
  @Operation(summary = "Ban user")
  public ResponseEntity<AdminUserBanResponse> banUser(
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
      @Valid @RequestBody AdminUserBanRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminUserBanRecord record =
        adminPlatformService.banUser(
            principal,
            requestId,
            idempotencyKey,
            userId,
            request.banType(),
            request.reason(),
            request.expiresAt(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminUserBanResponse.from(record));
  }

  @PostMapping("/{userId}/unban")
  @Operation(summary = "Unban user")
  public ResponseEntity<AdminUserBanResponse> unbanUser(
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
    AdminUserBanRecord record =
        adminPlatformService.unbanUser(
            principal,
            requestId,
            idempotencyKey,
            userId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminUserBanResponse.from(record));
  }

  @GetMapping("/banned")
  @Operation(summary = "List banned users")
  public ResponseEntity<AdminUserBanPageResponse> listBannedUsers(
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
      @RequestParam(name = "active", required = false) Boolean active) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminUserBanPage banPage =
        adminPlatformService.listBannedUsers(principal, page, pageSize, active);
    return ResponseEntity.ok(AdminUserBanPageResponse.from(banPage));
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

  public record AdminUserBanRequest(
      @NotBlank String banType,
      @NotBlank String reason,
      Instant expiresAt) {}

  public record AdminUserBanResponse(
      String id,
      String userId,
      String username,
      String banType,
      String reason,
      String bannedBy,
      Instant bannedAt,
      Instant expiresAt,
      String unbannedBy,
      Instant unbannedAt,
      boolean active) {

    static AdminUserBanResponse from(AdminUserBanRecord record) {
      return new AdminUserBanResponse(
          record.id(),
          record.userId(),
          record.username(),
          record.banType() != null ? record.banType().name() : null,
          record.reason(),
          record.bannedBy(),
          record.bannedAt(),
          record.expiresAt(),
          record.unbannedBy(),
          record.unbannedAt(),
          record.active());
    }
  }

  public record AdminUserBanPageResponse(
      List<AdminUserBanResponse> items,
      AdminPaginationResponse pagination) {

    static AdminUserBanPageResponse from(AdminUserBanPage page) {
      List<AdminUserBanResponse> items =
          page.items().stream().map(AdminUserBanResponse::from).toList();
      return new AdminUserBanPageResponse(
          items,
          new AdminPaginationResponse(
              page.page(), page.pageSize(), page.total(), page.totalPages()));
    }
  }

  public record AdminPaginationResponse(int page, int pageSize, int total, int totalPages) {}
}
