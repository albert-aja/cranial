package io.cranial.platform.document.model;

public record DocumentBatchEstimateResult(
        int documentCount,
        int estimatedPages,
        long totalBytes,
        long estimatedDurationMs,
        int estimatedMemoryMb,
        long estimatedTokens,
        long estimatedHostedCostCents,
        boolean localFirstExecution,
        String recommendedExecutionMode,
        String guidanceNote
) {
}

