package com.mingo.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM UserBlock b " +
            "WHERE (b.blocker.id = :a AND b.blocked.id = :b) OR (b.blocker.id = :b AND b.blocked.id = :a)")
    boolean existsEitherWay(@Param("a") UUID a, @Param("b") UUID b);
}
