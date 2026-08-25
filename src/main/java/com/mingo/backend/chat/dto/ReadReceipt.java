package com.mingo.backend.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ReadReceipt(UUID userId, Instant lastReadAt) {
}
