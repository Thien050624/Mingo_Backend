package com.mingo.backend.forum.dto;

import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.forum.ForumMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumMessageResponse(
        UUID id,
        ParticipantSummary sender,
        String text,
        String imageUrl,
        String fileUrl,
        String fileName,
        Long fileSize,
        String fileType,
        List<ParticipantSummary> likedBy,
        boolean reportedByMe,
        boolean recalled,
        Instant createdAt
) {
    public static ForumMessageResponse from(ForumMessage message, List<ParticipantSummary> likedBy, boolean reportedByMe) {
        boolean recalled = message.isRecalled();
        return new ForumMessageResponse(
                message.getId(),
                ParticipantSummary.from(message.getSender()),
                recalled ? null : message.getText(),
                recalled ? null : message.getImageUrl(),
                recalled ? null : message.getFileUrl(),
                recalled ? null : message.getFileName(),
                recalled ? null : message.getFileSize(),
                recalled ? null : message.getFileType(),
                likedBy,
                reportedByMe,
                recalled,
                message.getCreatedAt());
    }
}
