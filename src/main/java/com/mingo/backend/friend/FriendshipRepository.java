package com.mingo.backend.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :a AND f.addressee.id = :b) " +
            "OR (f.requester.id = :b AND f.addressee.id = :a)")
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT f FROM Friendship f WHERE f.status = :status AND (f.requester.id = :userId OR f.addressee.id = :userId)")
    List<Friendship> findAllByUserAndStatus(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    List<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
            "WHERE f.status = 'ACCEPTED' AND ((f.requester.id = :a AND f.addressee.id = :b) " +
            "OR (f.requester.id = :b AND f.addressee.id = :a))")
    boolean areFriends(@Param("a") UUID a, @Param("b") UUID b);
}
