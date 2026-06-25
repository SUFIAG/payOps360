package com.payOps.payops360.cache.application.port.output;

import java.time.Duration;
import java.util.Optional;

/**
 * Output Port: Cache Service
 * Abstraction for caching operations (Redis, Caffeine, etc.)
 */
public interface CacheService {
    <T> void put(String key, T value);
    <T> void put(String key, T value, Duration ttl);
    <T> Optional<T> get(String key, Class<T> type);
    void evict(String key);
    void evictAll(String pattern);
    boolean exists(String key);
}

