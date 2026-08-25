package com.mingo.backend.chat.dto;

import java.util.UUID;

public record TypingNotification(UUID conversationId, UUID userId, String name) {
}
