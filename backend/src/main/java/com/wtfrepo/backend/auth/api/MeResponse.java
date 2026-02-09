package com.wtfrepo.backend.auth.api;

import com.wtfrepo.backend.auth.domain.AuthUser;
import java.util.List;

public record MeResponse(
    String userId,
    String username,
    boolean usernameChanged,
    List<String> roles,
    long bugBalance) {

  public static MeResponse from(AuthUser user) {
    return new MeResponse(
        user.userId(),
        user.username(),
        user.usernameChanged(),
        user.roles().stream().map(Enum::name).toList(),
        user.bugBalance());
  }
}

