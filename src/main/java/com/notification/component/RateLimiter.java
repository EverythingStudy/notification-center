package com.notification.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis-based sliding window rate limiter per channel.
 * Uses a sorted-set (ZADD + ZREMRANGEBYSCORE) for atomic
 * distributed rate limiting across multiple service instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final long DEFAULT_WINDOW_MS = 1000;
    private static final long DEFAULT_MAX_PERMITS = 50;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LUA_TRY_ACQUIRE =
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local maxPermits = tonumber(ARGV[3]) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
            "local count = redis.call('ZCARD', key) " +
            "if count < maxPermits then " +
            "    redis.call('ZADD', key, now, now) " +
            "    redis.call('EXPIRE', key, math.ceil(window / 1000)) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    public boolean tryAcquire(String channelName, long maxPermits) {
        String key = "notify:ratelimit:" + channelName;
        long now = System.currentTimeMillis();
        long permits = maxPermits > 0 ? maxPermits : DEFAULT_MAX_PERMITS;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_TRY_ACQUIRE, Long.class);
        Long result = redisTemplate.execute(script, List.of(key),
                String.valueOf(now), String.valueOf(DEFAULT_WINDOW_MS), String.valueOf(permits));
        boolean acquired = result != null && result == 1L;
        if (!acquired) {
            log.warn("Rate limit exceeded for channel: {}", channelName);
        }
        return acquired;
    }

    public boolean tryAcquire(String channelName) {
        return tryAcquire(channelName, DEFAULT_MAX_PERMITS);
    }
}
