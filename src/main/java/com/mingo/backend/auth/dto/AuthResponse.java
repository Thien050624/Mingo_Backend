package com.mingo.backend.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String role,
        boolean onboarded
) {
}
