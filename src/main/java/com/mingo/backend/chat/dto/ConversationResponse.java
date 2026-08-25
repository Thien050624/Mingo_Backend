package com.mingo.backend.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        boolean group,
        String name,
        String avatarUrl,
        UUID createdBy,
        List<ParticipantSummary> participants,
        List<ReadReceipt> readReceipts,
        MessageResponse lastMessage,
        long unreadCount,
        boolean muted,
        Instant createdAt
) {
}
