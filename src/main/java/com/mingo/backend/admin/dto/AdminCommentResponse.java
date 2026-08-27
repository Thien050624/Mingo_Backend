package com.mingo.backend.admin.dto;

import com.mingo.backend.post.dto.AuthorSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminCommentResponse(
        UUID id,
        UUID postId,
        AuthorSummary author,
        String content,
        Instant createdAt,
        long reports,
        boolean hidden
) {
}
