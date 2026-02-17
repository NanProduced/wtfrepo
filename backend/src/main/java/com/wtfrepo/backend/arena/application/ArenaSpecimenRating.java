package com.wtfrepo.backend.arena.application;

/** Lightweight rating projection used by arena read/write services. */
public record ArenaSpecimenRating(
    String specimenId,
    int eloScore,
    long matchesPlayed,
    int recentAppearances) {}
