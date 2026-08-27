package com.mingo.backend.user.dto;

import com.mingo.backend.user.User;

import java.util.UUID;

public record UserSearchResponse(
        UUID id,
        String name,
        String avatar,
        String work,
        String location,
        long mutualFriendsCount,
        long postCount
) {
    public static UserSearchResponse from(User user, long mutualFriendsCount, long postCount) {
        return new UserSearchResponse(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getWork(),
                user.getLocation(),
                mutualFriendsCount,
                postCount);
    }
}
