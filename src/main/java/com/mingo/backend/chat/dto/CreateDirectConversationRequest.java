package com.mingo.backend.chat.dto;

import java.util.UUID;

public record CreateDirectConversationRequest(UUID otherUserId) {
}
