package com.wtfrepo.backend.comments.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentResonanceRequest(@NotBlank @Size(max = 128) String clientRequestId) {}

