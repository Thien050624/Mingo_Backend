package com.mingo.backend.forum.dto;

import com.mingo.backend.chat.dto.ParticipantSummary;

import java.util.List;
import java.util.UUID;

public record ForumMessageLikeEvent(UUID messageId, List<ParticipantSummary> likedBy) {
}
