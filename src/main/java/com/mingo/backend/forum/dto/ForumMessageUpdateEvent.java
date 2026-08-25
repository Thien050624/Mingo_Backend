package com.mingo.backend.forum.dto;

import java.util.UUID;

public record ForumMessageUpdateEvent(UUID messageId, boolean recalled, boolean hidden) {
}
