package com.mingo.backend.common.ratelimit;

import com.mingo.backend.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void allowsRequests_upToTheLimit() {
        RateLimiter rateLimiter = new RateLimiter();

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                rateLimiter.checkAllowed("key", 5, Duration.ofMinutes(1));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void throwsTooManyRequests_onceLimitExceeded() {
        RateLimiter rateLimiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkAllowed("key", 5, Duration.ofMinutes(1));
        }

        assertThatThrownBy(() -> rateLimiter.checkAllowed("key", 5, Duration.ofMinutes(1)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void tracksEachKeyIndependently() {
        RateLimiter rateLimiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkAllowed("key-a", 5, Duration.ofMinutes(1));
        }

        assertThatCode(() -> rateLimiter.checkAllowed("key-b", 5, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void resetsCount_afterWindowExpires() {
        RateLimiter rateLimiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkAllowed("key", 5, Duration.ofMillis(50));
        }

        assertThatThrownBy(() -> rateLimiter.checkAllowed("key", 5, Duration.ofMillis(50)))
                .isInstanceOf(ApiException.class);

        await(80);

        assertThatCode(() -> rateLimiter.checkAllowed("key", 5, Duration.ofMillis(50)))
                .doesNotThrowAnyException();
    }

    private void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
