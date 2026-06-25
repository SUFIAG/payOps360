package com.payOps/payops360.cache.adapter.output.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.payOps/payops360.cache.application.port.output.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Local/In-Memory Cache Service using Caffeine
 * For single-instance deployments or development
 */
@Component
@ConditionalOnProperty(name = "cache.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalCacheService implements CacheService {

    private final Cache<String, Object> cache;

    public LocalCacheService() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();

        log.info("Local cache initialized with Caffeine (max 10,000 entries, 1 hour TTL)");
    }

    @Override
    public <T> void put(String key, T value) {
        cache.put(key, value);
        log.debug("Cached locally: {}", key);
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        // Caffeine doesn't support per-key TTL easily, use default TTL
        cache.put(key, value);
        log.debug("Cached locally: {} (TTL not supported per-key)", key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = cache.getIfPresent(key);
        if (value != null) {
            log.debug("Cache hit: {}", key);
            return Optional.of((T) value);
        }
        log.debug("Cache miss: {}", key);
        return Optional.empty();
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
        log.debug("Evicted locally: {}", key);
    }

    @Override
    public void evictAll(String pattern) {
        cache.invalidateAll();
        log.info("Evicted all local cache entries");
    }

    @Override
    public boolean exists(String key) {
        return cache.getIfPresent(key) != null;
    }

    public void printStats() {
        log.info("Cache stats: {}", cache.stats());
    }
}

