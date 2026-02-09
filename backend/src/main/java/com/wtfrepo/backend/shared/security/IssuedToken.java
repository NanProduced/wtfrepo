package com.wtfrepo.backend.shared.security;

import java.time.Instant;

public record IssuedToken(String accessToken, long expiresIn, Instant expiresAt) {}

