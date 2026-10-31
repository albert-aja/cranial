package io.cranial.platform.provider.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record UsageSummaryView(
        @JsonAlias({"totalRequests", "total_requests"}) long totalRequests,
        @JsonAlias({"localRequests", "local_requests"}) long localRequests,
        @JsonAlias({"hostedRequests", "hosted_requests"}) long hostedRequests,
        @JsonAlias({"totalPromptTokens", "total_prompt_tokens"}) long totalPromptTokens,
        @JsonAlias({"totalCompletionTokens", "total_completion_tokens"}) long totalCompletionTokens,
        @JsonAlias({"totalTokens", "total_tokens"}) long totalTokens,
        @JsonAlias({"totalCostCents", "total_cost_cents"}) long totalCostCents,
        @JsonAlias({"fallbackBlockedCount", "fallback_blocked_count"}) long fallbackBlockedCount,
        @JsonAlias({"p50DurationMs", "p50_duration_ms"}) long p50DurationMs,
        @JsonAlias({"p95DurationMs", "p95_duration_ms"}) long p95DurationMs,
        @JsonAlias({"p99DurationMs", "p99_duration_ms"}) long p99DurationMs
) {
    public UsageSummaryView(
            long totalRequests,
            long localRequests,
            long hostedRequests,
            long totalPromptTokens,
            long totalCompletionTokens,
            long totalTokens,
            long totalCostCents,
            long fallbackBlockedCount
    ) {
        this(totalRequests, localRequests, hostedRequests, totalPromptTokens, totalCompletionTokens, totalTokens, totalCostCents, fallbackBlockedCount, 0, 0, 0);
    }
}
