package com.mingo.backend.admin.dto;

import com.mingo.backend.post.dto.AuthorSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminPostResponse(
        UUID id,
        AuthorSummary author,
        String content,
        String image,
        Instant createdAt,
        long likes,
        long comments,
        boolean hidden,
        long reports
) {
}
