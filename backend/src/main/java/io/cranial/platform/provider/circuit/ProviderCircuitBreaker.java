package io.cranial.platform.provider.circuit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ProviderCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String providerCode;
    private final int failureThreshold;
    private final long cooldownDurationMs;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTimestamp = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);

    public ProviderCircuitBreaker(String providerCode, int failureThreshold, long cooldownDurationMs) {
        this.providerCode = providerCode;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownDurationMs = Math.max(1000L, cooldownDurationMs);
    }

    public boolean canExecute() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }

        long now = System.currentTimeMillis();
        long elapsedSinceFailure = now - lastFailureTimestamp.get();

        if (current == State.OPEN) {
            if (elapsedSinceFailure >= cooldownDurationMs) {
                // Transition to HALF_OPEN for trial execution
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN allows trial execution
        return true;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        successfulCalls.incrementAndGet();
        state.set(State.CLOSED);
    }

    public void recordFailure() {
        lastFailureTimestamp.set(System.currentTimeMillis());
        int fails = consecutiveFailures.incrementAndGet();

        if (state.get() == State.HALF_OPEN || fails >= failureThreshold) {
            state.set(State.OPEN);
        }
    }

    public void reset() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    public State getState() {
        if (state.get() == State.OPEN && (System.currentTimeMillis() - lastFailureTimestamp.get()) >= cooldownDurationMs) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
        }
        return state.get();
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diag = new LinkedHashMap<>();
        State current = getState();
        diag.put("provider_code", providerCode);
        diag.put("state", current.name());
        diag.put("consecutive_failures", consecutiveFailures.get());
        diag.put("failure_threshold", failureThreshold);
        diag.put("successful_calls", successfulCalls.get());
        diag.put("last_failure", lastFailureTimestamp.get() > 0 ? Instant.ofEpochMilli(lastFailureTimestamp.get()).toString() : null);
        long elapsed = System.currentTimeMillis() - lastFailureTimestamp.get();
        long cooldownRemaining = Math.max(0, cooldownDurationMs - elapsed);
        diag.put("cooldown_remaining_ms", current == State.OPEN ? cooldownRemaining : 0);
        return diag;
    }
}

