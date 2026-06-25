package com.payOps.payops360.cache.adapter.output.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payOps/payops360.cache.application.port.output.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Redis Cache Service Implementation
 * Distributed caching for multi-instance deployments
 */
@Component
@ConditionalOnProperty(name = "cache.provider", havingValue = "redis", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService implements CacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> void put(String key, T value) {
        put(key, value, Duration.ofHours(1)); // Default TTL: 1 hour
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, jsonValue, ttl);
            log.debug("Cached: {} with TTL: {}", key, ttl);
        } catch (Exception e) {
            log.error("Failed to cache key: " + key, e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String jsonValue = redisTemplate.opsForValue().get(key);
            if (jsonValue != null) {
                T value = objectMapper.readValue(jsonValue, type);
                log.debug("Cache hit: {}", key);
                return Optional.of(value);
            }
            log.debug("Cache miss: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get cached key: " + key, e);
            return Optional.empty();
        }
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(key);
        log.debug("Evicted: {}", key);
    }

    @Override
    public void evictAll(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Evicted {} keys matching pattern: {}", keys.size(), pattern);
        }
    }

    @Override
    public boolean exists(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }
}

