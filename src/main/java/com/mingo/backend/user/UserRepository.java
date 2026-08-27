package com.mingo.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByCreatedAtAfter(Instant after);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    java.util.List<User> findByRoleOrderByEmailAsc(Role role);

    Page<User> findByRoleOrderByCreatedAtDesc(Role role, Pageable pageable);

    long countByRole(Role role);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY u.createdAt DESC")
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role = :role AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) ORDER BY u.createdAt DESC")
    Page<User> searchUsersByRole(@Param("query") String query, @Param("role") Role role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role <> com.mingo.backend.user.Role.ADMIN " +
            "AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND NOT EXISTS (SELECT b FROM UserBlock b WHERE " +
            "(b.blocker.id = u.id AND b.blocked.id = :viewerId) OR (b.blocker.id = :viewerId AND b.blocked.id = u.id)) " +
            "ORDER BY u.createdAt DESC")
    Page<User> searchUsersExcludingBlocked(@Param("query") String query, @Param("viewerId") UUID viewerId, Pageable pageable);

    @Query("SELECT u.createdAt FROM User u")
    java.util.List<Instant> findAllCreatedAt();
}
