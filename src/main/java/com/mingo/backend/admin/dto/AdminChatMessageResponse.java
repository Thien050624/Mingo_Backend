package com.mingo.backend.admin.dto;

import com.mingo.backend.chat.dto.ParticipantSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminChatMessageResponse(
        UUID id,
        UUID conversationId,
        ParticipantSummary sender,
        String content,
        Instant createdAt,
        long reports
) {
}
