package com.mingo.backend.friend.dto;

public record FriendshipStatusResponse(String status, boolean blockedByMe) {
    public FriendshipStatusResponse(String status) {
        this(status, false);
    }
}
