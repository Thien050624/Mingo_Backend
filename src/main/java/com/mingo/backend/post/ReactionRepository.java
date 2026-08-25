package com.mingo.backend.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    Optional<Reaction> findByPostIdAndUserId(UUID postId, UUID userId);

    List<Reaction> findByPostId(UUID postId);

    List<Reaction> findByPostIdIn(List<UUID> postIds);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}
