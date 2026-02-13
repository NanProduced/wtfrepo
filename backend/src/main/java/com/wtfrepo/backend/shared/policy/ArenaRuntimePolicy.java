package com.wtfrepo.backend.shared.policy;

import java.time.Duration;

/**
 * Runtime policy values consumed by arena-related modules.
 *
 * <p>Current implementation is property-backed and will be replaced by admin-published source
 * when M03 economy/policy module is ready.
 */
public record ArenaRuntimePolicy(Duration battleTtl, boolean settlementInProgress, int initialElo) {}

