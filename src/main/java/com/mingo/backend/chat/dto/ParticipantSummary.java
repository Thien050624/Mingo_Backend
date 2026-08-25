package com.mingo.backend.chat.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record ParticipantSummary(UUID id, String name, String avatar) {
    public static ParticipantSummary from(User user) {
        return new ParticipantSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl());
    }
}
