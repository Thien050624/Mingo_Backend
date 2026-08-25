package com.mingo.backend.forum;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumMessageLikeRepository extends JpaRepository<ForumMessageLike, UUID> {

    Optional<ForumMessageLike> findByMessageIdAndUserId(UUID messageId, UUID userId);

    List<ForumMessageLike> findByMessageId(UUID messageId);
}
