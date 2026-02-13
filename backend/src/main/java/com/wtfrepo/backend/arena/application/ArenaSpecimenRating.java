package com.wtfrepo.backend.arena.application;

/** Lightweight rating projection used by arena vote. */
public record ArenaSpecimenRating(String specimenId, int eloScore, long matchesPlayed) {}

