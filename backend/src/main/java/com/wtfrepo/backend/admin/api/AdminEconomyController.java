package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.AdminEconomyService;
import com.wtfrepo.backend.admin.application.AdminEconomyService.AdminGrantBugResult;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
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
import java.util.Arrays;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/economy")
@Tag(name = "Admin: Economy", description = "Admin economy grant/revoke endpoints.")
public class AdminEconomyController {

  private final AdminEconomyService adminEconomyService;

  public AdminEconomyController(AdminEconomyService adminEconomyService) {
    this.adminEconomyService = adminEconomyService;
  }

  @PostMapping("/grant-bug")
  @Operation(summary = "Grant or revoke bug balance")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Grant processed")})
  public ResponseEntity<AdminGrantBugResponse> grantBug(
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
      @Valid @RequestBody AdminGrantBugRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    AdminGrantBugResult result =
        adminEconomyService.grantBug(
            principal,
            requestId,
            idempotencyKey,
            request.userId(),
            request.delta(),
            request.reason(),
            request.note(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminGrantBugResponse.from(result));
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

  public record AdminGrantBugRequest(
      @NotBlank String userId,
      int delta,
      @NotBlank String reason,
      @Size(max = 200) String note) {}

  public record AdminGrantBugResponse(
      String ledgerId,
      String userId,
      int delta,
      String reason,
      long balanceAfter,
      String refId,
      String note) {

    static AdminGrantBugResponse from(AdminGrantBugResult result) {
      return new AdminGrantBugResponse(
          result.ledgerId(),
          result.userId(),
          result.delta(),
          result.reason(),
          result.balanceAfter(),
          result.refId(),
          result.note());
    }
  }
}
