package com.mingo.backend.post.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PostResponse(
        UUID id,
        AuthorSummary author,
        String content,
        List<String> images,
        String visibility,
        Map<String, Long> reactions,
        String myReaction,
        List<CommentResponse> comments,
        boolean reportedByMe,
        boolean savedByMe,
        Instant createdAt
) {
}
