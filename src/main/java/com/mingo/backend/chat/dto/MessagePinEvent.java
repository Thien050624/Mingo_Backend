package com.mingo.backend.chat.dto;

import java.util.UUID;

public record MessagePinEvent(UUID conversationId, UUID messageId, boolean pinned) {
}
