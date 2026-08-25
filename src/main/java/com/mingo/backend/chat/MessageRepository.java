package com.mingo.backend.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT DISTINCT m FROM Message m WHERE m.id IN (SELECT r.message.id FROM MessageReport r) ORDER BY m.createdAt DESC")
    Page<Message> findReportedOrderByCreatedAtDesc(Pageable pageable);

    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID conversationId, Instant after, Pageable pageable);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    Optional<Message> findTopByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID conversationId, Instant after);

    long countByConversationIdAndCreatedAtAfterAndSenderIdNot(UUID conversationId, Instant after, UUID senderId);

    long countByConversationIdAndCreatedAtAfter(UUID conversationId, Instant after);

    Page<Message> findByConversationIdAndRecalledFalseAndTextContainingIgnoreCaseOrderByCreatedAtDesc(
            UUID conversationId, String text, Pageable pageable);

    Page<Message> findByConversationIdAndRecalledFalseAndTextContainingIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID conversationId, String text, Instant after, Pageable pageable);

    List<Message> findByConversationIdAndPinnedTrueOrderByPinnedAtDesc(UUID conversationId);

    /**
     * The id of the newest message in each of {@code conversationIds}, respecting {@code userId}'s
     * clear-history cutoff for that conversation (if any) — i.e. the same message
     * {@code findTopByConversationId(AndCreatedAtAfter)OrderByCreatedAtDesc} would return per
     * conversation, but batched into one query instead of one per conversation.
     */
    @Query(value = "SELECT ranked.id FROM (" +
            "SELECT m.id AS id, " +
            "ROW_NUMBER() OVER (PARTITION BY m.conversation_id ORDER BY m.created_at DESC) AS rn " +
            "FROM messages m " +
            "JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id AND cp.user_id = :userId " +
            "WHERE m.conversation_id IN (:conversationIds) " +
            "AND (cp.cleared_at IS NULL OR m.created_at > cp.cleared_at)" +
            ") ranked WHERE ranked.rn = 1", nativeQuery = true)
    List<UUID> findLatestMessageIdPerConversation(@Param("conversationIds") List<UUID> conversationIds,
                                                    @Param("userId") UUID userId);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.id IN :ids")
    List<Message> findByIdInWithSender(@Param("ids") List<UUID> ids);

    /**
     * Unread count per conversation for {@code userId}, batched the same way — equivalent to
     * calling {@code countByConversationIdAndCreatedAtAfterAndSenderIdNot} once per conversation.
     */
    @Query(value = "SELECT m.conversation_id AS conversationId, COUNT(*) AS unreadCount FROM messages m " +
            "JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id AND cp.user_id = :userId " +
            "WHERE m.conversation_id IN (:conversationIds) AND m.sender_id != :userId " +
            "AND m.created_at > COALESCE(GREATEST(cp.last_read_at, cp.cleared_at), '-infinity'::timestamptz) " +
            "GROUP BY m.conversation_id", nativeQuery = true)
    List<ConversationUnreadCount> countUnreadPerConversation(@Param("conversationIds") List<UUID> conversationIds,
                                                               @Param("userId") UUID userId);
}
