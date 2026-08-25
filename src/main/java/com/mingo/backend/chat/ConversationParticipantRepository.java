package com.mingo.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    List<ConversationParticipant> findByConversationId(UUID conversationId);

    List<ConversationParticipant> findByConversationIdIn(List<UUID> conversationIds);

    List<ConversationParticipant> findByUserId(UUID userId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("SELECT cp1.conversation FROM ConversationParticipant cp1 JOIN ConversationParticipant cp2 " +
            "ON cp1.conversation.id = cp2.conversation.id " +
            "WHERE cp1.conversation.group = false AND cp1.user.id = :userA AND cp2.user.id = :userB")
    Optional<Conversation> findDirectConversation(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
