package com.mingo.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank String currentPassword
) {
}
