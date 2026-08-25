package com.mingo.backend.user.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String role,
        String displayName,
        String gender,
        String avatarUrl,
        String bio,
        String work,
        String location,
        boolean onboarded
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                user.getGender(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getWork(),
                user.getLocation(),
                user.isOnboarded());
    }
}
