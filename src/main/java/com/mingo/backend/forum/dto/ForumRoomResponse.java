package com.mingo.backend.forum.dto;

import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.forum.ForumRoom;

import java.time.Instant;
import java.util.UUID;

public record ForumRoomResponse(
        UUID id,
        String name,
        String description,
        ParticipantSummary createdBy,
        Instant createdAt
) {
    public static ForumRoomResponse from(ForumRoom room) {
        return new ForumRoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                ParticipantSummary.from(room.getCreatedBy()),
                room.getCreatedAt());
    }
}
