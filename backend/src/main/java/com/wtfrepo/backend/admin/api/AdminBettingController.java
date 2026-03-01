package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.AdminBettingService;
import com.wtfrepo.backend.admin.application.AdminBettingService.AdminForceSettleResult;
import com.wtfrepo.backend.admin.application.AdminBettingService.HouseConfigUpdateResult;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.economy.application.BettingService.HouseConfigRecord;
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
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
@RequestMapping("/api/v1/admin/betting")
@Tag(name = "Admin: Betting", description = "Admin betting endpoints.")
public class AdminBettingController {

  private final AdminBettingService adminBettingService;

  public AdminBettingController(AdminBettingService adminBettingService) {
    this.adminBettingService = adminBettingService;
  }

  @PostMapping("/force-settle")
  @Operation(summary = "Force settle betting pool")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Force settle executed")})
  public ResponseEntity<AdminForceSettleResponse> forceSettle(
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
      @Valid @RequestBody AdminForceSettleRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireSuperAdminPrincipal(jwt);
    AdminForceSettleResult result =
        adminBettingService.forceSettle(
            principal,
            requestId,
            idempotencyKey,
            request.specimenId(),
            request.reason(),
            request.confirm(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminForceSettleResponse.from(result));
  }

  @PostMapping("/house-config")
  @Operation(summary = "Update house fund config")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "House config updated")})
  public ResponseEntity<AdminHouseConfigResponse> updateHouseConfig(
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
      @Valid @RequestBody AdminHouseConfigRequest request,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireSuperAdminPrincipal(jwt);
    HouseConfigUpdateResult result =
        adminBettingService.updateHouseConfig(
            principal,
            requestId,
            idempotencyKey,
            request.specimenId(),
            request.houseBudget(),
            request.weightUp(),
            request.weightFlat(),
            request.weightDown(),
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminHouseConfigResponse.from(result));
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

  public record AdminForceSettleRequest(
      @NotBlank String specimenId,
      String reason,
      boolean confirm) {}

  public record AdminForceSettleResponse(
      String tradingDay,
      String outcome,
      String poolStatusBefore,
      String poolStatusAfter,
      int cancelledOrders,
      int refundedOrders,
      long refundedAmount,
      boolean settled) {

    static AdminForceSettleResponse from(AdminForceSettleResult result) {
      return new AdminForceSettleResponse(
          result.tradingDay(),
          result.outcome(),
          result.poolStatusBefore(),
          result.poolStatusAfter(),
          result.cancelledOrders(),
          result.refundedOrders(),
          result.refundedAmount(),
          result.settled());
    }
  }

  public record AdminHouseConfigRequest(
      @NotBlank String specimenId,
      long houseBudget,
      @NotNull BigDecimal weightUp,
      @NotNull BigDecimal weightFlat,
      @NotNull BigDecimal weightDown) {}

  public record AdminHouseConfigResponse(
      HouseConfigRecord before,
      HouseConfigRecord after) {

    static AdminHouseConfigResponse from(HouseConfigUpdateResult result) {
      return new AdminHouseConfigResponse(result.before(), result.after());
    }
  }
}
