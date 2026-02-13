package com.wtfrepo.backend.arena.api;

import jakarta.validation.constraints.NotBlank;

public record ArenaVoteRequest(@NotBlank String battleId, @NotBlank String winner) {}

