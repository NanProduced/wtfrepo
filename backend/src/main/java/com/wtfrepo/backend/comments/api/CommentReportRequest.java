package com.wtfrepo.backend.comments.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentReportRequest(
    @NotBlank @Size(max = 32) String reasonCode,
    @Size(max = 1000) String message,
    @NotBlank @Size(max = 128) String clientRequestId) {}

