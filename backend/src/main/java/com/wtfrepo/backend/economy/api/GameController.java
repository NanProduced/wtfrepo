package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyGameService;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.economy.application.support.EconomyExceptions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Game APIs owned by economy module (M03 core). */
@RestController
@Validated
@RequestMapping("/api/v1/game")
public class GameController {

  private final EconomyGameService economyGameService;

  public GameController(EconomyGameService economyGameService) {
    this.economyGameService = economyGameService;
  }

  @GetMapping("/types")
  public ResponseEntity<GameTypesResponse> listTypes(@AuthenticationPrincipal Jwt jwt) {
    String userId = resolveUserId(jwt);
    EconomyGameService.GameTypesView result = economyGameService.listGameTypes(userId);
    return ResponseEntity.ok(GameTypesResponse.from(result));
  }

  @PostMapping("/score")
  public ResponseEntity<GameScoreResponse> submitScore(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = EconomyConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Valid @RequestBody GameScoreRequest request) {
    String userId = requireUserId(jwt);
    EconomyGameService.SubmitScoreResult result =
        economyGameService.submitScore(
            userId,
            idempotencyKey,
            new EconomyGameService.SubmitScoreCommand(
                request.gameType(),
                request.score(),
                request.durationMs(),
                request.clientSessionId(),
                request.extraData()));
    return ResponseEntity.ok(GameScoreResponse.from(result));
  }

  private String requireUserId(Jwt jwt) {
    String userId = resolveUserId(jwt);
    if (!StringUtils.hasText(userId)) {
      throw EconomyExceptions.unauthorized(EconomyConstants.Message.AUTH_REQUIRED);
    }
    return userId;
  }

  private String resolveUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      return null;
    }
    return jwt.getSubject();
  }
}

