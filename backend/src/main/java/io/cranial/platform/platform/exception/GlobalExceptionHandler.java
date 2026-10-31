package io.cranial.platform.platform.exception;

import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CranialException.class)
    public ResponseEntity<ApiResponse<Void>> handleCranialException(CranialException ex) {
        ErrorCode code = ex.errorCode();
        HttpHeaders headers = new HttpHeaders();
        if (ex.retryAfterSeconds() != null) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        ErrorDetail detail = ErrorDetail.of(code.name(), ex.getMessage(), code.retryable());
        return ResponseEntity.status(code.httpStatus())
                .headers(headers)
                .body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorDetail.field(
                        ErrorCode.VALIDATION_ERROR.name(),
                        fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"
                ))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> ErrorDetail.field(
                        ErrorCode.VALIDATION_ERROR.name(),
                        v.getPropertyPath().toString(),
                        v.getMessage()
                ))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(details));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        ErrorDetail detail = ErrorDetail.field(
                ErrorCode.VALIDATION_ERROR.name(),
                ex.getHeaderName(),
                "Required header '" + ex.getHeaderName() + "' is missing"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedBody(HttpMessageNotReadableException ex) {
        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.MALFORMED_REQUEST.name(),
                "Malformed JSON request body",
                false
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayloadTooLarge(MaxUploadSizeExceededException ex) {
        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.PAYLOAD_TOO_LARGE.name(),
                "Request payload exceeds configured size limit",
                false
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        ErrorDetail detail = ErrorDetail.of(ErrorCode.UNAUTHORIZED.name(), "Authentication required", false);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.FORBIDDEN.name(),
                "Insufficient permissions to perform requested operation",
                false
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                "Endpoint or resource not found: " + ex.getResourcePath(),
                false
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(List.of(detail)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled platform error type={}", ex.getClass().getSimpleName(), ex);
        ErrorDetail detail = ErrorDetail.of(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected internal platform error occurred",
                true
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(List.of(detail)));
    }
}

