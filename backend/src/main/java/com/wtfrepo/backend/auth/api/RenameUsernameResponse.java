package com.wtfrepo.backend.auth.api;

import com.wtfrepo.backend.auth.domain.AuthUser;

public record RenameUsernameResponse(String username, boolean usernameChanged) {

  public static RenameUsernameResponse from(AuthUser user) {
    return new RenameUsernameResponse(user.username(), user.usernameChanged());
  }
}

