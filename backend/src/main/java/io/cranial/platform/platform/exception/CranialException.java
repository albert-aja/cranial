package io.cranial.platform.platform.exception;

public class CranialException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;

    public CranialException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = null;
    }

    public CranialException(ErrorCode errorCode, String message, long retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

