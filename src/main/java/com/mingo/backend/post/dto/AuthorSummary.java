package com.mingo.backend.post.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record AuthorSummary(UUID id, String name, String avatar) {
    public static AuthorSummary from(User user) {
        return new AuthorSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl());
    }
}
