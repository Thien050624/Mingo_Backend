package com.mingo.backend.common.ratelimit;

import com.mingo.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory fixed-window rate limiter. Suitable for a single backend
 * instance (no shared/distributed state); would need a Redis-backed limiter
 * if the app is ever scaled to multiple instances.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void checkAllowed(String key, int maxAttempts, Duration window) {
        Instant now = Instant.now();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart.plus(window))) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (w.count.get() > maxAttempts) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã thao tác quá nhiều lần, vui lòng thử lại sau ít phút");
        }
    }

    private static final class Window {
        private final Instant windowStart;
        private final AtomicInteger count = new AtomicInteger(1);

        private Window(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
