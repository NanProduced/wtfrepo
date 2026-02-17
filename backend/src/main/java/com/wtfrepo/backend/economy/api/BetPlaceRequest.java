package com.wtfrepo.backend.economy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Place-bet request payload. */
public record BetPlaceRequest(
    @NotBlank String specimenId,
    @NotBlank String direction,
    @Positive int amount) {}
