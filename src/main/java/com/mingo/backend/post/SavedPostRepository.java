package com.mingo.backend.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, UUID> {

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    Optional<SavedPost> findByUserIdAndPostId(UUID userId, UUID postId);

    List<SavedPost> findByUserIdAndPostIdIn(UUID userId, List<UUID> postIds);

    @Query("SELECT sp FROM SavedPost sp JOIN FETCH sp.post p JOIN FETCH p.author WHERE sp.user.id = :userId " +
            "ORDER BY sp.createdAt DESC")
    Page<SavedPost> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
