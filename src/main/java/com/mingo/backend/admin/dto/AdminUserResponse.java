package com.mingo.backend.admin.dto;

import com.mingo.backend.user.User;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String name,
        String email,
        String avatar,
        Instant joined,
        long posts,
        String status,
        String role
) {
    public static AdminUserResponse from(User user, long postCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                postCount,
                user.getStatus().name(),
                user.getRole().name());
    }
}
