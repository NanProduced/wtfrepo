package com.wtfrepo.backend.auth.api;

import com.wtfrepo.backend.auth.application.AuthService;
import com.wtfrepo.backend.auth.application.support.AuthConstants;
import com.wtfrepo.backend.auth.domain.AuthUser;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/auth/exchange")
  // BFF-only endpoint: exchanges upstream OAuth proof for backend access token.
  public ResponseEntity<AuthExchangeResponse> exchange(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(name = AuthConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Valid @RequestBody AuthExchangeRequest request) {
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    var result = authService.exchange(request, requestId, resolvedIdempotencyKey);
    return ResponseEntity.ok(AuthExchangeResponse.from(result));
  }

  @GetMapping("/me")
  // Returns current authenticated user profile.
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    AuthUser user = authService.me(jwt);
    return ResponseEntity.ok(MeResponse.from(user));
  }

  @PatchMapping("/me/username")
  // Supports one-time username rename with conflict/validation checks.
  public ResponseEntity<RenameUsernameResponse> renameUsername(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RenameUsernameRequest request) {
    AuthUser user = authService.rename(jwt.getSubject(), request.username(), requestId);
    return ResponseEntity.ok(RenameUsernameResponse.from(user));
  }

  private String resolveIdempotencyKey(String requestId, String idempotencyKey) {
    if (StringUtils.hasText(idempotencyKey)) {
      return idempotencyKey.trim();
    }
    return requestId;
  }
}
