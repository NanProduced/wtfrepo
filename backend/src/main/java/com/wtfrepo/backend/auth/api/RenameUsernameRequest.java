package com.wtfrepo.backend.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RenameUsernameRequest(
    @NotBlank
    @Size(min = 4, max = 24)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username contains invalid characters")
    String username) {}

