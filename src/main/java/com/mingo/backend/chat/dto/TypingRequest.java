package com.mingo.backend.chat.dto;

import java.util.UUID;

public record TypingRequest(UUID conversationId) {
}
