package com.wtfrepo.backend.arena.api;

import com.wtfrepo.backend.arena.application.ArenaDuelService;
import com.wtfrepo.backend.arena.application.ArenaVoteService;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaExceptions;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/arena")
public class ArenaController {

  private final ArenaVoteService arenaVoteService;
  private final ArenaDuelService arenaDuelService;

  public ArenaController(ArenaVoteService arenaVoteService, ArenaDuelService arenaDuelService) {
    this.arenaVoteService = arenaVoteService;
    this.arenaDuelService = arenaDuelService;
  }

  @GetMapping("/duel")
  public ResponseEntity<ArenaDuelResponse> duel(
      @RequestHeader(name = RequestIdConstants.HEADER_NAME, required = false) String requestId,
      @RequestParam(name = "excludeSpecimenIds", required = false) List<String> excludeSpecimenIds,
      @RequestParam(name = "excludeCombinations", required = false) List<String> excludeCombinations,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest) {
    String resolvedRequestId = StringUtils.hasText(requestId) ? requestId.trim() : "";
    String userId = resolveUserId(jwt);
    String clientIp = resolveClientIp(servletRequest);

    ArenaDuelService.DuelResult result =
        arenaDuelService.duel(
            resolvedRequestId,
            new ArenaDuelService.DuelQuery(
                parseCsvSet(excludeSpecimenIds),
                parseCsvSet(excludeCombinations),
                userId,
                clientIp));
    return ResponseEntity.ok(ArenaDuelResponse.from(result));
  }

  @PostMapping("/vote")
  public ResponseEntity<ArenaVoteResponse> vote(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(name = ArenaConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ArenaVoteRequest request) {
    String userId = requireUserId(jwt);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey, userId, request.battleId());
    ArenaVoteService.VoteResult result =
        arenaVoteService.vote(
            requestId,
            userId,
            resolvedIdempotencyKey,
            new ArenaVoteService.VoteCommand(request.battleId(), request.winner()));
    return ResponseEntity.ok(ArenaVoteResponse.from(result));
  }

  private String requireUserId(Jwt jwt) {
    String userId = resolveUserId(jwt);
    if (!StringUtils.hasText(userId)) {
      throw ArenaExceptions.unauthorized(ArenaConstants.Message.AUTH_REQUIRED);
    }
    return userId;
  }

  private String resolveUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      return null;
    }
    return jwt.getSubject();
  }

  private Set<String> parseCsvSet(List<String> rawItems) {
    if (rawItems == null || rawItems.isEmpty()) {
      return Set.of();
    }
    Set<String> result = new LinkedHashSet<>();
    for (String rawItem : rawItems) {
      if (!StringUtils.hasText(rawItem)) {
        continue;
      }
      Arrays.stream(rawItem.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .forEach(result::add);
    }
    return result;
  }

  private String resolveClientIp(HttpServletRequest servletRequest) {
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

  private String resolveIdempotencyKey(
      String requestId, String idempotencyKey, String userId, String battleId) {
    if (StringUtils.hasText(idempotencyKey)) {
      return idempotencyKey.trim();
    }
    if (StringUtils.hasText(battleId) && StringUtils.hasText(userId)) {
      return battleId.trim() + "_" + userId.trim();
    }
    return requestId;
  }
}
