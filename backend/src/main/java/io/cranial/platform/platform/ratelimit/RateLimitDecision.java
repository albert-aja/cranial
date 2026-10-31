package io.cranial.platform.platform.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        int limitRpm,
        int remaining,
        long retryAfterSeconds
) {
}

