package com.wtfrepo.backend.notifications.api;

import com.wtfrepo.backend.notifications.application.NotificationStreamService;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Unified SSE stream gateway for notifications/narrator/ticker/wallet. */
@RestController
@RequestMapping("/api/v1")
public class NotificationStreamController {

  private final NotificationStreamService streamService;

  public NotificationStreamController(NotificationStreamService streamService) {
    this.streamService = streamService;
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "Open SSE stream")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Stream opened")})
  public ResponseEntity<SseEmitter> stream(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = "Last-Event-ID",
              description = "Last received event id for reconnect.")
          @RequestHeader(name = "Last-Event-ID", required = false)
          String lastEventId,
      @RequestParam(name = "channels", required = false) String channels,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest request) {
    String userId = resolveUserId(jwt);
    boolean authenticated = StringUtils.hasText(userId);
    Set<String> resolvedChannels = streamService.resolveChannels(channels, authenticated);
    SseEmitter emitter = streamService.openStream(userId, resolvedChannels);
    return ResponseEntity.ok(emitter);
  }

  private String resolveUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      return null;
    }
    return jwt.getSubject().trim();
  }
}
