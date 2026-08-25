package com.mingo.backend.friend.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(UUID friendshipId, UserSummary from, Instant createdAt) {
}
