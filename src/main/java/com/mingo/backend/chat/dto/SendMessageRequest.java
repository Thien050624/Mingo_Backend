package com.mingo.backend.chat.dto;

import java.util.UUID;

public record SendMessageRequest(
        String text,
        String imageUrl,
        String fileUrl,
        String fileName,
        Long fileSize,
        String fileType,
        UUID replyToMessageId
) {
}
