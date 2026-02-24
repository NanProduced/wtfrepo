package com.wtfrepo.backend.comments.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentPublishRequest(
    @NotBlank String specimenId,
    @NotBlank @Size(max = 4000) String contentMd,
    @Size(max = 64) String replyToCommentId,
    @NotBlank @Size(max = 128) String clientRequestId) {}

