package com.mingo.backend.admin.dto;

import com.mingo.backend.admin.AdminAction;
import com.mingo.backend.admin.AdminAuditLog;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogResponse(
        UUID id,
        String adminName,
        String adminEmail,
        AdminAction action,
        String targetType,
        UUID targetId,
        String details,
        Instant createdAt
) {
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdmin().getDisplayName(),
                log.getAdmin().getEmail(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetails(),
                log.getCreatedAt());
    }
}
