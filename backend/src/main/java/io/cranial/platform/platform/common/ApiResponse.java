package io.cranial.platform.platform.common;

import io.cranial.platform.platform.observability.CorrelationIdHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiResponse<T>(
        String requestId,
        String correlationId,
        String status,
        T data,
        PageMeta pagination,
        List<ErrorDetail> errors,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        String corr = CorrelationIdHolder.currentOrGenerate();
        return new ApiResponse<>(UUID.randomUUID().toString(), corr, "ok", data, null, List.of(), Instant.now());
    }

    public static <T> ApiResponse<List<T>> paged(List<T> items, PageMeta pagination) {
        String corr = CorrelationIdHolder.currentOrGenerate();
        return new ApiResponse<>(UUID.randomUUID().toString(), corr, "ok", items, pagination, List.of(), Instant.now());
    }

    public static <T> ApiResponse<T> error(List<ErrorDetail> errors) {
        String corr = CorrelationIdHolder.currentOrGenerate();
        return new ApiResponse<>(UUID.randomUUID().toString(), corr, "error", null, null, errors, Instant.now());
    }
}

