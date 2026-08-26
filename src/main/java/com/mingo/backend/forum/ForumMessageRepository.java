package com.mingo.backend.forum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ForumMessageRepository extends JpaRepository<ForumMessage, UUID> {

    Page<ForumMessage> findByRoomIdAndHiddenFalseOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

    Page<ForumMessage> findByRoomIdAndHiddenFalseAndRecalledFalseAndTextContainingIgnoreCaseOrderByCreatedAtDesc(
            UUID roomId, String text, Pageable pageable);

    long countByRoomIdAndCreatedAtAfter(UUID roomId, Instant after);

    @Query("SELECT DISTINCT m FROM ForumMessage m WHERE m.id IN (SELECT r.message.id FROM ForumMessageReport r) ORDER BY m.createdAt DESC")
    Page<ForumMessage> findReportedOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM ForumMessage m WHERE m.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") UUID roomId);
}
