package io.cranial.platform.platform.common;

public record ErrorDetail(
        String code,
        String message,
        String field,
        boolean retryable
) {
    public static ErrorDetail of(String code, String message, boolean retryable) {
        return new ErrorDetail(code, message, null, retryable);
    }

    public static ErrorDetail field(String code, String field, String message) {
        return new ErrorDetail(code, message, field, false);
    }
}

