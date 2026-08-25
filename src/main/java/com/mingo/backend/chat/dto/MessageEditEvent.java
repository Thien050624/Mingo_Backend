package com.mingo.backend.chat.dto;

import java.util.UUID;

public record MessageEditEvent(UUID conversationId, UUID messageId, String text) {
}
