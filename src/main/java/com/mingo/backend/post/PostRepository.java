package com.mingo.backend.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE (p.hidden = false OR p.author.id = :viewerId) " +
            "AND (p.visibility = :publicVisibility OR p.author.id = :viewerId OR " +
            "(p.visibility = 'FRIENDS' AND EXISTS (SELECT f FROM Friendship f WHERE f.status = 'ACCEPTED' " +
            "AND ((f.requester.id = p.author.id AND f.addressee.id = :viewerId) " +
            "OR (f.requester.id = :viewerId AND f.addressee.id = p.author.id))))) " +
            "AND (p.author.id = :viewerId OR NOT EXISTS (SELECT b FROM UserBlock b " +
            "WHERE (b.blocker.id = p.author.id AND b.blocked.id = :viewerId) " +
            "OR (b.blocker.id = :viewerId AND b.blocked.id = p.author.id))) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> findFeedVisibleTo(@Param("publicVisibility") PostVisibility publicVisibility,
                                 @Param("viewerId") UUID viewerId,
                                 Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.author.id = :authorId " +
            "AND (p.hidden = false OR p.author.id = :viewerId) " +
            "AND (p.visibility = :publicVisibility OR p.author.id = :viewerId OR " +
            "(p.visibility = 'FRIENDS' AND EXISTS (SELECT f FROM Friendship f WHERE f.status = 'ACCEPTED' " +
            "AND ((f.requester.id = p.author.id AND f.addressee.id = :viewerId) " +
            "OR (f.requester.id = :viewerId AND f.addressee.id = p.author.id))))) " +
            "AND (p.author.id = :viewerId OR NOT EXISTS (SELECT b FROM UserBlock b " +
            "WHERE (b.blocker.id = p.author.id AND b.blocked.id = :viewerId) " +
            "OR (b.blocker.id = :viewerId AND b.blocked.id = p.author.id))) " +
            "ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorVisibleTo(@Param("authorId") UUID authorId,
                                      @Param("publicVisibility") PostVisibility publicVisibility,
                                      @Param("viewerId") UUID viewerId,
                                      Pageable pageable);

    long countByAuthorId(UUID authorId);

    long countByCreatedAtAfter(Instant after);

    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Post> findByHiddenTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<Post> findByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT DISTINCT p FROM Post p WHERE p.id IN (SELECT r.post.id FROM PostReport r) ORDER BY p.createdAt DESC")
    Page<Post> findReportedOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE (p.hidden = false OR p.author.id = :viewerId) " +
            "AND (p.visibility = :publicVisibility OR p.author.id = :viewerId OR " +
            "(p.visibility = 'FRIENDS' AND EXISTS (SELECT f FROM Friendship f WHERE f.status = 'ACCEPTED' " +
            "AND ((f.requester.id = p.author.id AND f.addressee.id = :viewerId) " +
            "OR (f.requester.id = :viewerId AND f.addressee.id = p.author.id))))) " +
            "AND (p.author.id = :viewerId OR NOT EXISTS (SELECT b FROM UserBlock b " +
            "WHERE (b.blocker.id = p.author.id AND b.blocked.id = :viewerId) " +
            "OR (b.blocker.id = :viewerId AND b.blocked.id = p.author.id))) " +
            "AND LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.createdAt DESC")
    Page<Post> searchVisibleTo(@Param("query") String query,
                               @Param("publicVisibility") PostVisibility publicVisibility,
                               @Param("viewerId") UUID viewerId,
                               Pageable pageable);
}
