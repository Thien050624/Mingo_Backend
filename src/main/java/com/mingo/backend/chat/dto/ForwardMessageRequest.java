package com.mingo.backend.chat.dto;

import java.util.UUID;

public record ForwardMessageRequest(UUID sourceMessageId) {
}
