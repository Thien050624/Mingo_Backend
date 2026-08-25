package com.mingo.backend.admin.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record AdminSummaryResponse(UUID id, String name, String email) {
    public static AdminSummaryResponse from(User user) {
        return new AdminSummaryResponse(user.getId(), user.getDisplayName(), user.getEmail());
    }
}
