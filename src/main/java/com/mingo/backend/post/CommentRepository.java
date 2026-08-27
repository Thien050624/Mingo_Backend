package com.mingo.backend.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(UUID postId);

    List<Comment> findByPostIdAndParentCommentIsNullAndHiddenFalseOrderByCreatedAtAsc(UUID postId);

    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId);

    List<Comment> findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(UUID parentCommentId);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post.id IN :postIds AND c.parentComment IS NULL " +
            "AND c.hidden = false ORDER BY c.createdAt ASC")
    List<Comment> findByPostIdInAndParentCommentIsNullAndHiddenFalseOrderByCreatedAtAsc(@Param("postIds") List<UUID> postIds);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.parentComment.id IN :parentCommentIds " +
            "AND c.hidden = false ORDER BY c.createdAt ASC")
    List<Comment> findByParentCommentIdInAndHiddenFalseOrderByCreatedAtAsc(@Param("parentCommentIds") List<UUID> parentCommentIds);

    @Query("SELECT DISTINCT c FROM Comment c WHERE c.id IN (SELECT r.comment.id FROM CommentReport r) " +
            "ORDER BY c.createdAt DESC")
    Page<Comment> findReportedOrderByCreatedAtDesc(Pageable pageable);
}
