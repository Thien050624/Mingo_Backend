package com.mingo.backend.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentReportRepository extends JpaRepository<CommentReport, UUID> {

    boolean existsByCommentIdAndReporterId(UUID commentId, UUID reporterId);

    long countByCommentId(UUID commentId);

    void deleteByCommentIdAndReporterId(UUID commentId, UUID reporterId);

    List<CommentReport> findByCommentIdInAndReporterId(List<UUID> commentIds, UUID reporterId);

    @Query("SELECT r.comment.id AS commentId, COUNT(r) AS reportCount FROM CommentReport r " +
            "WHERE r.comment.id IN :commentIds GROUP BY r.comment.id")
    List<CommentReportCount> countByCommentIdIn(@Param("commentIds") List<UUID> commentIds);
}
