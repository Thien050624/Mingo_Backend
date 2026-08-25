package com.mingo.backend.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadReceiptEvent(UUID conversationId, UUID userId, Instant lastReadAt) {
}
