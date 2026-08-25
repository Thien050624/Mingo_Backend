package com.mingo.backend.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {

    Optional<CommentLike> findByCommentIdAndUserId(UUID commentId, UUID userId);

    long countByCommentId(UUID commentId);

    void deleteByCommentIdAndUserId(UUID commentId, UUID userId);

    @Query("SELECT cl.comment.id AS commentId, COUNT(cl) AS likeCount FROM CommentLike cl " +
            "WHERE cl.comment.id IN :commentIds GROUP BY cl.comment.id")
    List<CommentLikeCount> countByCommentIdIn(@Param("commentIds") List<UUID> commentIds);

    List<CommentLike> findByCommentIdInAndUserId(List<UUID> commentIds, UUID userId);
}
