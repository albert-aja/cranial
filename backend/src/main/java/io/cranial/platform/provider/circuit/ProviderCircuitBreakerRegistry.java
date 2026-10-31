package io.cranial.platform.provider.circuit;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ProviderCircuitBreakerRegistry {

    private final ConcurrentMap<String, ProviderCircuitBreaker> breakers = new ConcurrentHashMap<>();

    public ProviderCircuitBreaker getOrCreate(String providerCode) {
        return breakers.computeIfAbsent(providerCode.toLowerCase(), k -> new ProviderCircuitBreaker(k, 3, 30_000L));
    }

    public ProviderCircuitBreaker getOrCreate(String providerCode, int threshold, long cooldownMs) {
        return breakers.computeIfAbsent(providerCode.toLowerCase(), k -> new ProviderCircuitBreaker(k, threshold, cooldownMs));
    }

    public Map<String, Object> getAllDiagnostics() {
        Map<String, Object> map = new LinkedHashMap<>();
        breakers.forEach((code, breaker) -> map.put(code, breaker.getDiagnostics()));
        return map;
    }
}

