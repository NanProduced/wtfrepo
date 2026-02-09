package com.wtfrepo.backend.auth.api;

import com.wtfrepo.backend.auth.domain.OAuthProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthExchangeRequest(
    @NotNull OAuthProvider provider,
    @NotBlank String identityProof,
    @NotBlank String oauthState,
    @Valid Profile profile) {

  public record Profile(String displayName, String avatarUrl) {}
}

