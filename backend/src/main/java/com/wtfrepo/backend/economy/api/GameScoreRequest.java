package com.wtfrepo.backend.economy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

/** Submit-score request payload for economy mini-game APIs. */
public record GameScoreRequest(
    @NotBlank String gameType,
    @PositiveOrZero int score,
    @Positive int durationMs,
    @NotBlank String clientSessionId,
    Map<String, Object> extraData) {}
