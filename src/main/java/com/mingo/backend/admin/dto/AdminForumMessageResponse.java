package com.mingo.backend.admin.dto;

import com.mingo.backend.chat.dto.ParticipantSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminForumMessageResponse(
        UUID id,
        UUID roomId,
        String roomName,
        ParticipantSummary author,
        String content,
        Instant createdAt,
        long reports,
        boolean hidden
) {
}
