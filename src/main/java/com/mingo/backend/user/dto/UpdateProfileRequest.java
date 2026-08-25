package com.mingo.backend.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 20) String gender,
        @Size(max = 500) String avatarUrl,
        @Size(max = 500) String bio,
        @Size(max = 150) String work,
        @Size(max = 150) String location
) {
}
