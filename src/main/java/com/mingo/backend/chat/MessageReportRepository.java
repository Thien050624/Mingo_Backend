package com.mingo.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {

    boolean existsByMessageIdAndReporterId(UUID messageId, UUID reporterId);

    List<MessageReport> findByMessageIdInAndReporterId(List<UUID> messageIds, UUID reporterId);

    long countByMessageId(UUID messageId);

    void deleteByMessageId(UUID messageId);

    void deleteByMessageIdAndReporterId(UUID messageId, UUID reporterId);
}
