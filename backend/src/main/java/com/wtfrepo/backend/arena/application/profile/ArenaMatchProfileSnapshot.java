package com.wtfrepo.backend.arena.application.profile;

/** Immutable snapshot used by Arena matching and audit flows. */
public record ArenaMatchProfileSnapshot(
    String profileVersion, String speciesDimensionKey, String diagnosisDimensionKey) {}

