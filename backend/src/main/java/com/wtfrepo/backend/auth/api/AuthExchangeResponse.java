package com.wtfrepo.backend.auth.api;

import com.wtfrepo.backend.auth.application.AuthService.ExchangeResult;
import java.time.Instant;
import java.util.List;

public record AuthExchangeResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt,
    boolean isNewUser,
    User user,
    int initialBugGrant) {

  public static AuthExchangeResponse from(ExchangeResult result) {
    return new AuthExchangeResponse(
        result.issuedToken().accessToken(),
        "Bearer",
        result.issuedToken().expiresIn(),
        result.issuedToken().expiresAt(),
        result.isNewUser(),
        new User(
            result.authUser().userId(),
            result.authUser().username(),
            result.authUser().usernameChanged(),
            result.authUser().roles().stream().map(Enum::name).toList()),
        result.initialBugGrant());
  }

  public record User(String userId, String username, boolean usernameChanged, List<String> roles) {}
}

