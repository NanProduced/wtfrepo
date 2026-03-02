package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.BettingService;
import com.wtfrepo.backend.economy.application.support.BettingConstants;
import com.wtfrepo.backend.economy.application.support.BettingExceptions;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Betting APIs for economy MVP endpoints. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class BettingController {

  private final BettingService bettingService;

  public BettingController(BettingService bettingService) {
    this.bettingService = bettingService;
  }

  @PostMapping("/bet")
  public ResponseEntity<BetPlaceResponse> placeBet(
      @RequestHeader(name = RequestIdConstants.HEADER_NAME, required = false) String requestId,
      @RequestHeader(name = BettingConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody BetPlaceRequest request) {
    String userId = requireUserId(jwt);
    String resolvedIdempotencyKey = resolveIdempotencyKey(idempotencyKey, requestId, userId, request);
    BettingService.PlaceBetResult result =
        bettingService.placeBet(
            requestId,
            userId,
            resolvedIdempotencyKey,
            new BettingService.PlaceBetCommand(request.specimenId(), request.direction(), request.amount()));
    return ResponseEntity.status(HttpStatus.CREATED).body(BetPlaceResponse.from(result));
  }

  @GetMapping("/bet/active")
  public ResponseEntity<BetActiveResponse> activeBets(@AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    BettingService.ActiveBetsView view = bettingService.getActiveBets(userId);
    return ResponseEntity.ok(BetActiveResponse.from(view));
  }

  @GetMapping("/bet/history")
  public ResponseEntity<BetHistoryResponse> history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
    String userId = requireUserId(jwt);
    BettingService.HistoryBetsView view = bettingService.getHistory(userId, cursor, limit);
    return ResponseEntity.ok(BetHistoryResponse.from(view));
  }

  @GetMapping("/settlement/today")
  public ResponseEntity<SettlementTodayResponse> settlementToday(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
    String userId = requireUserId(jwt);
    BettingService.SettlementTodayView view = bettingService.getSettlementToday(userId, cursor, limit);
    return ResponseEntity.ok(SettlementTodayResponse.from(view));
  }

  @GetMapping("/specimens/{specimenId}/bet-summary")
  public ResponseEntity<BetSummaryResponse> summary(
      @PathVariable String specimenId, @AuthenticationPrincipal Jwt jwt) {
    BettingService.BetSummaryView summary =
        bettingService.getBetSummary(specimenId, resolveOptionalUserId(jwt));
    return ResponseEntity.ok(BetSummaryResponse.from(summary));
  }

  private String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw BettingExceptions.unauthorized(BettingConstants.Message.AUTH_REQUIRED);
    }
    return jwt.getSubject();
  }

  private String resolveOptionalUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      return null;
    }
    return jwt.getSubject();
  }

  private String resolveIdempotencyKey(
      String idempotencyKey,
      String requestId,
      String userId,
      BetPlaceRequest request) {
    if (StringUtils.hasText(idempotencyKey)) {
      return idempotencyKey.trim();
    }
    if (StringUtils.hasText(requestId)) {
      return requestId.trim();
    }
    return "bet:"
        + userId
        + ":"
        + request.specimenId().trim()
        + ":"
        + request.direction().trim().toUpperCase()
        + ":"
        + request.amount();
  }
}
