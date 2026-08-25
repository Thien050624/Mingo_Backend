package com.mingo.backend.notification.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record ActorSummary(UUID id, String name, String avatar) {
    public static ActorSummary from(User user) {
        return new ActorSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl());
    }
}
