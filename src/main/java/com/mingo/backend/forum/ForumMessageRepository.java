package com.mingo.backend.forum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface ForumMessageRepository extends JpaRepository<ForumMessage, UUID> {

    Page<ForumMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ForumMessage> findByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<ForumMessage> findByHiddenFalseAndRecalledFalseAndTextContainingIgnoreCaseOrderByCreatedAtDesc(String text, Pageable pageable);

    long countByCreatedAtAfter(Instant after);

    @Query("SELECT DISTINCT m FROM ForumMessage m WHERE m.id IN (SELECT r.message.id FROM ForumMessageReport r) ORDER BY m.createdAt DESC")
    Page<ForumMessage> findReportedOrderByCreatedAtDesc(Pageable pageable);
}
