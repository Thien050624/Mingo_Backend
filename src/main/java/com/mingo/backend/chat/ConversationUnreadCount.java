package com.mingo.backend.chat;

import java.util.UUID;

public interface ConversationUnreadCount {
    UUID getConversationId();
    long getUnreadCount();
}
