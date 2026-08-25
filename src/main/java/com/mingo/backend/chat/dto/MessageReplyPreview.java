package com.mingo.backend.chat.dto;

import com.mingo.backend.chat.Message;

import java.util.UUID;

public record MessageReplyPreview(
        UUID id,
        String senderName,
        String text,
        boolean hasImage,
        boolean hasFile,
        boolean recalled
) {
    public static MessageReplyPreview from(Message message) {
        boolean recalled = message.isRecalled();
        return new MessageReplyPreview(
                message.getId(),
                message.getSender().getDisplayName(),
                recalled ? null : message.getText(),
                !recalled && message.getImageUrl() != null,
                !recalled && message.getFileUrl() != null,
                recalled);
    }
}
