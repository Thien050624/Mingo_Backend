package com.mingo.backend.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostReportRepository extends JpaRepository<PostReport, UUID> {

    boolean existsByPostIdAndReporterId(UUID postId, UUID reporterId);

    long countByPostId(UUID postId);

    void deleteByPostIdAndReporterId(UUID postId, UUID reporterId);

    List<PostReport> findByPostIdInAndReporterId(List<UUID> postIds, UUID reporterId);
}
