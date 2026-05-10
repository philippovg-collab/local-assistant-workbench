package com.example.demo.api;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.JsonRequestSizeLimitFilter.RequestPayloadTooLargeException;
import com.example.demo.config.RequestContext;
import com.example.demo.config.ChatAuditProperties;
import com.example.demo.error.CodedException;
import com.example.demo.error.ErrorType;
import com.example.demo.service.AuditRedactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
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
    private final AuditRedactionService redactionService;

    @Autowired
    public ApiExceptionHandler(
        MaterialProperties materialProperties,
        ObjectProvider<AuditRedactionService> redactionServiceProvider
    ) {
        this.materialProperties = materialProperties;
        this.redactionService = redactionServiceProvider.getIfAvailable(
            () -> new AuditRedactionService(new ChatAuditProperties())
        );
    }

    public ApiExceptionHandler(MaterialProperties materialProperties) {
        this.materialProperties = materialProperties;
        this.redactionService = new AuditRedactionService(new ChatAuditProperties());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return buildErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(CodedException.class)
    public ResponseEntity<ErrorResponse> handleCodedException(CodedException exception, HttpServletRequest request) {
        return buildErrorResponse(statusOf(exception.getType()), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        logger.warn("Rejected oversized upload: {}", redactionService.redactStoredText(exception.getMessage()));
        return buildErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "material.upload_too_large",
            "Uploaded file exceeds the configured size limit of " + materialProperties.getMaxUploadBytes() + " bytes",
            request
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(
        MissingServletRequestPartException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "material.missing_file_part",
            "Multipart request is missing required '" + exception.getRequestPartName() + "' part",
            request
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(
        MultipartException exception,
        HttpServletRequest request
    ) {
        logger.warn("Rejected malformed multipart request: {}", redactionService.redactStoredText(exception.getMessage()));
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "material.invalid_multipart",
            "Multipart request is malformed or unreadable",
            request
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
                request
            );
        }
        logger.warn(
            "Rejected unreadable request payload on {} rootCause={}: {}",
            request.getRequestURI(),
            rootCause.getClass().getName(),
            redactionService.redactStoredText(rootCause.getMessage())
        );
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "request.invalid_payload",
            "Request payload is malformed or contains invalid values",
            request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String field = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField())
            .orElse("request");
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "request.validation_failed",
            "Request field '" + field + "' is invalid",
            request
        );
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(Exception exception, HttpServletRequest request) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "request.validation_failed",
            exception.getMessage() == null ? "Request payload contains invalid values" : exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "request.not_found",
            "Requested API resource does not exist",
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(
            HttpStatus.METHOD_NOT_ALLOWED,
            "request.method_not_supported",
            "HTTP method '" + exception.getMethod() + "' is not supported for this resource",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        String requestId = RequestContext.requestId(request);
        Throwable rootCause = rootCauseOf(exception);

        logger.error(
            "Unexpected error [{}] on {} contentLength={} rootCause={}: {}",
            requestId,
            request.getRequestURI(),
            request.getContentLengthLong(),
            rootCause.getClass().getName(),
            redactionService.redactStoredText(rootCause.getMessage()),
            exception
        );

        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal.unexpected_error",
            "Unexpected server error",
            request
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        String requestId = RequestContext.requestId(request);
        return ResponseEntity.status(status)
            .header(RequestContext.REQUEST_ID_HEADER, requestId)
            .body(new ErrorResponse(
                code,
                redactionService.redactStoredText(message),
                Instant.now().toString(),
                requestId
            ));
    }

    private HttpStatus statusOf(ErrorType type) {
        return switch (type) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case REQUEST_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case PROVIDER_BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL, STORAGE_FAILURE, INVALID_CONFIGURATION -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
