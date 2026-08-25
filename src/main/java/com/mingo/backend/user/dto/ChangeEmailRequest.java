package com.mingo.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
        @NotBlank @Email String newEmail,
        @NotBlank String currentPassword
) {
}
