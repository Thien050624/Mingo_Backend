package com.mingo.backend.chat.dto;

import com.mingo.backend.chat.Message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        ParticipantSummary sender,
        String text,
        String imageUrl,
        String fileUrl,
        String fileName,
        Long fileSize,
        String fileType,
        MessageReplyPreview replyTo,
        boolean forwarded,
        boolean pinned,
        boolean edited,
        String type,
        boolean recalled,
        List<ParticipantSummary> likedBy,
        boolean reportedByMe,
        Instant createdAt
) {
    public static MessageResponse from(Message message, List<ParticipantSummary> likedBy, boolean reportedByMe) {
        boolean recalled = message.isRecalled();
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                ParticipantSummary.from(message.getSender()),
                recalled ? null : message.getText(),
                recalled ? null : message.getImageUrl(),
                recalled ? null : message.getFileUrl(),
                recalled ? null : message.getFileName(),
                recalled ? null : message.getFileSize(),
                recalled ? null : message.getFileType(),
                recalled || message.getReplyTo() == null ? null : MessageReplyPreview.from(message.getReplyTo()),
                message.isForwarded(),
                !recalled && message.isPinned(),
                message.isEdited(),
                message.getType().name(),
                recalled,
                likedBy,
                reportedByMe,
                message.getCreatedAt());
    }
}
