package com.wtfrepo.backend.auth.application;

import com.wtfrepo.backend.auth.domain.AuthUser;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.auth.domain.UserRole;

import java.util.Set;

public record AuthUserRecord(
    String userId,
    OAuthProvider provider,
    String providerSubject,
    String username,
    boolean usernameChanged,
    long bugBalance,
    boolean isNewUser) {

  public AuthUser toAuthUser() {
    return new AuthUser(
        userId,
        username,
        usernameChanged,
        Set.of(UserRole.USER),
        bugBalance);
  }
}

