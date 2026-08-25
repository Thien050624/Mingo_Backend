package com.mingo.backend.friend.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record UserSummary(UUID id, String name, String avatar, String work, String location) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl(), user.getWork(), user.getLocation());
    }
}
