package com.wtfrepo.backend.auth.application.economy;

/**
 * Auth-facing bridge for economy constants and bootstrap grants.
 *
 * <p>TODO(M03-economy): replace with economy module source of truth
 * (`baseAmount * BUG_DENOM_MULTIPLIER` and dynamic policies).
 */
public interface AuthEconomyBridge {

  int initialBugGrantForNewUser();
}
