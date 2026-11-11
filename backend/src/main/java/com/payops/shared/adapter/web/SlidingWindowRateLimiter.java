package com.payops.shared.adapter.web;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter. Suitable for a single instance or as a
 * first line of defence; in a multi-replica deployment pair it with the
 * gateway/WAF limiter or move the buckets to Redis.
 */
public class SlidingWindowRateLimiter {

    private static final int MAX_TRACKED_KEYS = 100_000;

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Records a hit and throws when the key exceeded its budget.
     */
    public void acquire(String key) {
        long now = clock.millis();
        long windowStart = now - window.toMillis();
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.clear();
        }
        Deque<Long> hits = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.pollFirst();
            }
            if (hits.size() >= limit) {
                long retryAfterMillis = hits.peekFirst() + window.toMillis() - now;
                throw new RateLimitExceededException(Math.max(1, retryAfterMillis / 1000));
            }
            hits.addLast(now);
        }
    }
}
