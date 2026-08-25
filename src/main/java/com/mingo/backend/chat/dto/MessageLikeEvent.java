package com.mingo.backend.chat.dto;

import java.util.List;
import java.util.UUID;

public record MessageLikeEvent(UUID conversationId, UUID messageId, List<ParticipantSummary> likedBy) {
}
