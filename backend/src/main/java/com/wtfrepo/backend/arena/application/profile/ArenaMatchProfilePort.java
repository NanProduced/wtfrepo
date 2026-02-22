package com.wtfrepo.backend.arena.application.profile;

/**
 * Provides current Arena match profile snapshot sourced from M04 specimen config domain.
 */
public interface ArenaMatchProfilePort {

  ArenaMatchProfileSnapshot currentProfile();
}

