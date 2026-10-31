package io.cranial.platform.platform.ratelimit;

public interface RateLimiter {

    RateLimitDecision tryAcquire(String bucketKey, int limitPerMinute);

    void reset(String bucketKey);
}

