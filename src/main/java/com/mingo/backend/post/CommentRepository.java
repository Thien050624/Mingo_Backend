package com.mingo.backend.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(UUID postId);

    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post.id IN :postIds AND c.parentComment IS NULL " +
            "ORDER BY c.createdAt ASC")
    List<Comment> findByPostIdInAndParentCommentIsNullOrderByCreatedAtAsc(@Param("postIds") List<UUID> postIds);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.parentComment.id IN :parentCommentIds " +
            "ORDER BY c.createdAt ASC")
    List<Comment> findByParentCommentIdInOrderByCreatedAtAsc(@Param("parentCommentIds") List<UUID> parentCommentIds);
}
