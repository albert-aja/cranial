package io.cranial.platform.platform.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Distributed atomic token bucket rate limiter backed by Redis Lua script.
 * Used when running multiple horizontal replicas of CRANIAL.
 * Gracefully degrades to local in-memory limiting if Redis is temporarily unreachable.
 */
@Component
@ConditionalOnProperty(prefix = "cranial.ratelimit", name = "provider", havingValue = "redis")
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private static final String LUA_TOKEN_BUCKET = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            local interval = tonumber(ARGV[3])
            
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1])
            local last_refill = tonumber(data[2])
            
            if not tokens then
                tokens = capacity
                last_refill = now
            else
                local elapsed = math.max(0, now - last_refill)
                local refill = elapsed * (capacity / interval)
                tokens = math.min(capacity, tokens + refill)
                last_refill = now
            end
            
            if tokens >= 1.0 then
                tokens = tokens - 1.0
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                redis.call('EXPIRE', key, interval * 2)
                return {1, capacity, math.floor(tokens), 0}
            else
                local deficit = 1.0 - tokens
                local wait_sec = math.ceil(deficit * (interval / capacity))
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                redis.call('EXPIRE', key, interval * 2)
                return {0, capacity, 0, wait_sec}
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> redisScript;
    private final InMemoryTokenBucketRateLimiter fallbackLimiter;

    @SuppressWarnings("unchecked")
    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LUA_TOKEN_BUCKET);
        this.redisScript.setResultType(List.class);
        this.fallbackLimiter = new InMemoryTokenBucketRateLimiter();
    }

    @Override
    public RateLimitDecision tryAcquire(String bucketKey, int limitPerMinute) {
        int safeLimit = Math.max(1, limitPerMinute);
        String redisKey = "cranial:ratelimit:" + bucketKey;
        long now = Instant.now().getEpochSecond();

        try {
            List<?> result = redisTemplate.execute(
                    redisScript,
                    List.of(redisKey),
                    String.valueOf(safeLimit),
                    String.valueOf(now),
                    "60"
            );

            if (result != null && result.size() >= 4) {
                boolean allowed = ((Number) result.get(0)).intValue() == 1;
                int capacity = ((Number) result.get(1)).intValue();
                int remaining = ((Number) result.get(2)).intValue();
                long waitSec = ((Number) result.get(3)).longValue();
                return new RateLimitDecision(allowed, capacity, remaining, waitSec);
            }
        } catch (Exception ex) {
            log.warn("Redis rate limiter execution failed for key={}, falling back to in-memory limit", bucketKey, ex);
        }

        return fallbackLimiter.tryAcquire(bucketKey, safeLimit);
    }

    @Override
    public void reset(String bucketKey) {
        try {
            redisTemplate.delete("cranial:ratelimit:" + bucketKey);
        } catch (Exception ex) {
            log.warn("Failed to reset Redis rate limit key {}", bucketKey, ex);
        }
        fallbackLimiter.reset(bucketKey);
    }
}

