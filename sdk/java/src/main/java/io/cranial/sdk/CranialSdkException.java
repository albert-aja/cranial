package io.cranial.sdk;

public class CranialSdkException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public CranialSdkException(int statusCode, String errorCode, String message) {
        super(String.format("CRANIAL error (status=%d, code=%s): %s", statusCode, errorCode, message));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public CranialSdkException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = "SDK_INTERNAL_ERROR";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

