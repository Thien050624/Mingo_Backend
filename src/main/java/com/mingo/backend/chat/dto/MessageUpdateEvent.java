package com.mingo.backend.chat.dto;

import java.util.UUID;

public record MessageUpdateEvent(UUID conversationId, UUID messageId, boolean recalled) {
}
