package com.mingo.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageLikeRepository extends JpaRepository<MessageLike, UUID> {

    List<MessageLike> findByMessageId(UUID messageId);

    List<MessageLike> findByMessageIdIn(List<UUID> messageIds);

    Optional<MessageLike> findByMessageIdAndUserId(UUID messageId, UUID userId);
}
