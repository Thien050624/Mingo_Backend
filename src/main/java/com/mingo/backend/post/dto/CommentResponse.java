package com.mingo.backend.post.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        AuthorSummary author,
        String content,
        Instant createdAt,
        long likeCount,
        boolean likedByMe,
        List<CommentResponse> replies
) {
}
