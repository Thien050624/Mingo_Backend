package com.mingo.backend.forum;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ForumMessageReportRepository extends JpaRepository<ForumMessageReport, UUID> {

    boolean existsByMessageIdAndReporterId(UUID messageId, UUID reporterId);

    long countByMessageId(UUID messageId);

    void deleteByMessageId(UUID messageId);

    void deleteByMessageIdAndReporterId(UUID messageId, UUID reporterId);
}
