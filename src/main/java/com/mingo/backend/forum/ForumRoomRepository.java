package com.mingo.backend.forum;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ForumRoomRepository extends JpaRepository<ForumRoom, UUID> {

    @Query("SELECT r FROM ForumRoom r JOIN FETCH r.createdBy ORDER BY r.createdAt DESC")
    List<ForumRoom> findAllByOrderByCreatedAtDesc();
}
