package io.cranial.platform.platform.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "cranial.ratelimit", name = "provider", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private static final long MINUTE_NS = 60_000_000_000L;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitDecision tryAcquire(String bucketKey, int limitPerMinute) {
        int safeLimit = Math.max(1, limitPerMinute);
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> new Bucket(safeLimit));
        return bucket.consume(safeLimit);
    }

    @Override
    public void reset(String bucketKey) {
        buckets.remove(bucketKey);
    }

    private static final class Bucket {
        private double tokens;
        private int lastCapacity;
        private long lastRefillNs;

        private Bucket(int initialCapacity) {
            this.tokens = initialCapacity;
            this.lastCapacity = initialCapacity;
            this.lastRefillNs = System.nanoTime();
        }

        private synchronized RateLimitDecision consume(int capacity) {
            if (capacity != lastCapacity) {
                if (capacity > lastCapacity) {
                    tokens = Math.min(capacity, tokens + (capacity - lastCapacity));
                } else {
                    tokens = Math.min(capacity, tokens);
                }
                lastCapacity = capacity;
            }

            long now = System.nanoTime();
            long elapsedNs = Math.max(0L, now - lastRefillNs);
            double refill = ((double) elapsedNs / MINUTE_NS) * capacity;
            if (refill > 0) {
                tokens = Math.min(capacity, tokens + refill);
                lastRefillNs = now;
            }
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return new RateLimitDecision(true, capacity, (int) Math.floor(tokens), 0L);
            }
            double deficit = 1.0d - tokens;
            double nsPerToken = (double) MINUTE_NS / capacity;
            long waitSec = Math.max(1L, (long) Math.ceil((deficit * nsPerToken) / 1_000_000_000.0d));
            return new RateLimitDecision(false, capacity, 0, waitSec);
        }
    }
}
