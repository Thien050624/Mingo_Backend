package com.mingo.backend.notification.dto;

import com.mingo.backend.notification.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        ActorSummary actor,
        UUID postId,
        UUID commentId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                ActorSummary.from(n.getActor()),
                n.getPost() != null ? n.getPost().getId() : null,
                n.getComment() != null ? n.getComment().getId() : null,
                n.isRead(),
                n.getCreatedAt());
    }
}
