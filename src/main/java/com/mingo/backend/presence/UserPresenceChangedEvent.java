package com.mingo.backend.presence;

import java.util.UUID;

/**
 * Internal Spring application event (not a STOMP payload) fired when a user's aggregate
 * online/offline state actually flips — i.e. their active WebSocket session count crosses
 * 0. Listeners decide who should be told and how (see friend.FriendService).
 */
public record UserPresenceChangedEvent(UUID userId, boolean online) {
}
