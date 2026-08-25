package com.mingo.backend.presence;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Tracks which users currently have at least one open WebSocket session. In-memory only —
 * suitable for a single backend instance (see RateLimiter for the same caveat); a multi-instance
 * deployment would need this shared (e.g. Redis) instead.
 */
@Service
public class OnlinePresenceService {

    private final Map<UUID, AtomicInteger> sessionCounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionToUser = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public OnlinePresenceService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void connect(String sessionId, UUID userId) {
        sessionToUser.put(sessionId, userId);
        int count = sessionCounts.computeIfAbsent(userId, k -> new AtomicInteger()).incrementAndGet();
        if (count == 1) {
            eventPublisher.publishEvent(new UserPresenceChangedEvent(userId, true));
        }
    }

    public void disconnect(String sessionId) {
        UUID userId = sessionToUser.remove(sessionId);
        if (userId == null) return;
        AtomicInteger counter = sessionCounts.get(userId);
        if (counter == null) return;
        if (counter.decrementAndGet() <= 0) {
            sessionCounts.remove(userId);
            eventPublisher.publishEvent(new UserPresenceChangedEvent(userId, false));
        }
    }

    public boolean isOnline(UUID userId) {
        return sessionCounts.containsKey(userId);
    }

    public Set<UUID> onlineAmong(Collection<UUID> userIds) {
        return userIds.stream().filter(sessionCounts::containsKey).collect(Collectors.toSet());
    }
}
