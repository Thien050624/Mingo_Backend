package com.mingo.backend.friend.dto;

import java.util.UUID;

public record PresenceMessage(UUID userId, boolean online) {
}
