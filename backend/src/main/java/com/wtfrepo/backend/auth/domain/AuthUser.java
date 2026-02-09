package com.wtfrepo.backend.auth.domain;

import java.util.Set;

/**
 * Auth-facing user view used by token issuing and `/me` response.
 *
 * @param userId internal stable user id
 * @param username display username visible in product
 * @param usernameChanged whether one-time rename has been consumed
 * @param roles backend roles used for authorization decisions
 * @param bugBalance current bug balance snapshot
 */
public record AuthUser(
    String userId,
    String username,
    boolean usernameChanged,
    Set<UserRole> roles,
    long bugBalance) {}
