package com.mingo.backend.forum.dto;

public record SendForumMessageRequest(
        String text,
        String imageUrl,
        String fileUrl,
        String fileName,
        Long fileSize,
        String fileType
) {
}
