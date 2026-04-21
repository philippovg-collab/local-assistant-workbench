package com.example.demo.api;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.JsonRequestSizeLimitFilter.RequestPayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MaterialProperties materialProperties;

    public ApiExceptionHandler(MaterialProperties materialProperties) {
        this.materialProperties = materialProperties;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return buildErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        logger.warn("Rejected oversized upload: {}", exception.getMessage());
        return buildErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "material.upload_too_large",
            "Uploaded file exceeds the configured size limit of " + materialProperties.getMaxUploadBytes() + " bytes",
            null
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(MissingServletRequestPartException exception) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "material.missing_file_part",
            "Multipart request is missing required '" + exception.getRequestPartName() + "' part",
            null
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException exception) {
        logger.warn("Rejected malformed multipart request: {}", exception.getMessage());
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "material.invalid_multipart",
            "Multipart request is malformed or unreadable",
            null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        Throwable rootCause = rootCauseOf(exception);
        if (rootCause instanceof RequestPayloadTooLargeException) {
            return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "request.payload_too_large",
                "JSON request body exceeds the configured size limit",
                null
            );
        }
        logger.warn(
            "Rejected unreadable request payload on {} rootCause={}: {}",
            request.getRequestURI(),
            rootCause.getClass().getName(),
            rootCause.getMessage()
        );
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "request.invalid_payload",
            "Request payload is malformed or contains invalid values",
            null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String field = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField())
            .orElse("request");
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "request.validation_failed",
            "Request field '" + field + "' is invalid",
            null
        );
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(Exception exception) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "request.validation_failed",
            exception.getMessage() == null ? "Request payload contains invalid values" : exception.getMessage(),
            null
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        return buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "request.not_found",
            "Requested API resource does not exist",
            null
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return buildErrorResponse(
            HttpStatus.METHOD_NOT_ALLOWED,
            "request.method_not_supported",
            "HTTP method '" + exception.getMethod() + "' is not supported for this resource",
            null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        String requestId = UUID.randomUUID().toString();
        Throwable rootCause = rootCauseOf(exception);

        logger.error(
            "Unexpected error [{}] on {} contentLength={} rootCause={}: {}",
            requestId,
            request.getRequestURI(),
            request.getContentLengthLong(),
            rootCause.getClass().getName(),
            rootCause.getMessage(),
            exception
        );

        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal.unexpected_error",
            "Unexpected server error",
            requestId
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status,
        String code,
        String message,
        String requestId
    ) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(
                code,
                message,
                Instant.now().toString(),
                requestId
            ));
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
