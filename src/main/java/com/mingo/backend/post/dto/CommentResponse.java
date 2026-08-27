package com.mingo.backend.post.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        AuthorSummary author,
        String content,
        String imageUrl,
        Instant createdAt,
        long likeCount,
        boolean likedByMe,
        boolean reportedByMe,
        List<CommentResponse> replies
) {
}
