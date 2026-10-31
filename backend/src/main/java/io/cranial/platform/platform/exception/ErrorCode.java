package io.cranial.platform.platform.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, false),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, false),
    CREDENTIAL_REVOKED(HttpStatus.UNAUTHORIZED, false),
    CREDENTIAL_EXPIRED(HttpStatus.UNAUTHORIZED, false),
    INVALID_CONTEXT_SIGNATURE(HttpStatus.UNAUTHORIZED, false),
    CONTEXT_TIMESTAMP_EXPIRED(HttpStatus.UNAUTHORIZED, false),
    MISSING_SIGNED_CONTEXT(HttpStatus.UNAUTHORIZED, false),

    FORBIDDEN(HttpStatus.FORBIDDEN, false),
    APP_SUSPENDED(HttpStatus.FORBIDDEN, false),
    APP_IDENTITY_MISMATCH(HttpStatus.FORBIDDEN, false),
    CAPABILITY_NOT_ENTITLED(HttpStatus.FORBIDDEN, false),
    TENANT_NOT_AUTHORIZED(HttpStatus.FORBIDDEN, false),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    CONFLICT(HttpStatus.CONFLICT, false),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, false),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, false),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, false),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, false),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, true),

    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, true),
    PROVIDER_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, true),
    MODEL_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    MODEL_NOT_ALLOWED(HttpStatus.FORBIDDEN, false),
    ROUTING_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    HOSTED_PROVIDER_BLOCKED(HttpStatus.FORBIDDEN, false),
    SENSITIVE_DATA_BLOCKED(HttpStatus.FORBIDDEN, false),
    FALLBACK_BLOCKED(HttpStatus.FORBIDDEN, false),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, true),
    CONTEXT_WINDOW_EXCEEDED(HttpStatus.BAD_REQUEST, false),

    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    PROMPT_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    SCHEMA_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    SCHEMA_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, false),
    TOOL_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    TOOL_EXECUTION_FAILED(HttpStatus.BAD_GATEWAY, true),
    TOOL_PERMISSION_DENIED(HttpStatus.FORBIDDEN, false),
    ORCHESTRATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, true),

    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, false),
    FILE_SIGNATURE_MISMATCH(HttpStatus.BAD_REQUEST, false),
    INVALID_PAGE_RANGE(HttpStatus.BAD_REQUEST, false),
    TRANSFER_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, false),
    TRANSFER_TOKEN_EXPIRED(HttpStatus.GONE, false),
    DOCUMENT_EXPIRED(HttpStatus.GONE, false),
    DOCUMENT_PROCESSING_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, true),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    JOB_NOT_RESUMABLE(HttpStatus.CONFLICT, false),

    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    KNOWLEDGE_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    KNOWLEDGE_SOURCE_REVOKED(HttpStatus.GONE, false),
    KNOWLEDGE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, false),
    KNOWLEDGE_INDEXING_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, true),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, true);

    private final HttpStatus httpStatus;
    private final boolean retryable;

    ErrorCode(HttpStatus httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}

