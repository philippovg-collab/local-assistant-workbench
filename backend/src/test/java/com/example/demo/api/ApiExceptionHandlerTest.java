package com.example.demo.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.MaterialProperties;
import com.example.demo.error.ErrorType;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ProviderException;
import com.example.demo.error.StorageException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MaterialProperties properties = new MaterialProperties();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ExceptionThrowingController())
            .setControllerAdvice(new ApiExceptionHandler(properties))
            .build();
    }

    @Test
    void mapsOversizedUploadsToPayloadTooLarge() throws Exception {
        mockMvc.perform(get("/test/errors/oversize"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("material.upload_too_large"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mapsMalformedMultipartRequestsToBadRequest() throws Exception {
        mockMvc.perform(get("/test/errors/invalid-multipart"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.invalid_multipart"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mapsMissingMultipartPartsToBadRequest() throws Exception {
        mockMvc.perform(get("/test/errors/missing-part"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.missing_file_part"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mapsUnexpectedErrorsToInternalServerErrorWithRequestId() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal.unexpected_error"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mapsMissingResourcesToNotFoundWithRequestId() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("request.not_found"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mapsUnsupportedMethodsToMethodNotAllowedWithRequestId() throws Exception {
        mockMvc.perform(get("/test/errors/method-not-supported"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("request.method_not_supported"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void mapsApplicationExceptionsWithoutChangingErrorShape() throws Exception {
        mockMvc.perform(get("/test/errors/application-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("reference_workspace.default_required"))
            .andExpect(jsonPath("$.message").value("Default reference workspace must be active"))
            .andExpect(jsonPath("$.timestamp").isString())
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @ParameterizedTest
    @MethodSource("codedErrorMappings")
    void mapsCodedExceptionsWithoutLeakingHttpIntoLowerLayers(String error, int expectedStatus) throws Exception {
        mockMvc.perform(get("/test/errors/coded/{error}", error))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.code").value("test." + error))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    static Stream<Arguments> codedErrorMappings() {
        return Stream.of(
            Arguments.of("invalid-request", 400),
            Arguments.of("not-found", 404),
            Arguments.of("conflict", 409),
            Arguments.of("payload-too-large", 413),
            Arguments.of("request-timeout", 408),
            Arguments.of("provider-bad-response", 502),
            Arguments.of("provider-unavailable", 503),
            Arguments.of("provider-timeout", 504),
            Arguments.of("internal", 500),
            Arguments.of("storage-failure", 500),
            Arguments.of("invalid-configuration", 500)
        );
    }

    @RestController
    static class ExceptionThrowingController {

        @GetMapping("/test/errors/oversize")
        void oversize() {
            throw new MaxUploadSizeExceededException(2_000_001L);
        }

        @GetMapping("/test/errors/invalid-multipart")
        void invalidMultipart() {
            throw new MultipartException("broken multipart");
        }

        @GetMapping("/test/errors/missing-part")
        void missingPart() throws MissingServletRequestPartException {
            throw new MissingServletRequestPartException("file");
        }

        @GetMapping("/test/errors/unexpected")
        void unexpected() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/test/errors/not-found")
        void notFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.PUT, "/api/materials/material-1");
        }

        @GetMapping("/test/errors/method-not-supported")
        void methodNotSupported() throws HttpRequestMethodNotSupportedException {
            throw new HttpRequestMethodNotSupportedException("PUT", List.of("GET"));
        }

        @GetMapping("/test/errors/application-conflict")
        void applicationConflict() {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "reference_workspace.default_required",
                "Default reference workspace must be active"
            );
        }

        @GetMapping("/test/errors/coded/{error}")
        void coded(@PathVariable String error) {
            throw switch (error) {
                case "invalid-request" -> new ApplicationException(
                    ErrorType.INVALID_REQUEST,
                    "test." + error,
                    "Invalid request"
                );
                case "not-found" -> new ApplicationException(
                    ErrorType.NOT_FOUND,
                    "test." + error,
                    "Not found"
                );
                case "conflict" -> new ApplicationException(
                    ErrorType.CONFLICT,
                    "test." + error,
                    "Conflict"
                );
                case "payload-too-large" -> new ApplicationException(
                    ErrorType.PAYLOAD_TOO_LARGE,
                    "test." + error,
                    "Payload too large"
                );
                case "request-timeout" -> new ApplicationException(
                    ErrorType.REQUEST_TIMEOUT,
                    "test." + error,
                    "Request timeout"
                );
                case "provider-bad-response" -> new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    "test." + error,
                    "Bad provider response"
                );
                case "provider-unavailable" -> new ProviderException(
                    ErrorType.PROVIDER_UNAVAILABLE,
                    "test." + error,
                    "Provider unavailable"
                );
                case "provider-timeout" -> new ProviderException(
                    ErrorType.PROVIDER_TIMEOUT,
                    "test." + error,
                    "Provider timeout"
                );
                case "internal" -> new ApplicationException(
                    ErrorType.INTERNAL,
                    "test." + error,
                    "Internal"
                );
                case "storage-failure" -> new StorageException(
                    ErrorType.STORAGE_FAILURE,
                    "test." + error,
                    "Storage failure"
                );
                case "invalid-configuration" -> new ApplicationException(
                    ErrorType.INVALID_CONFIGURATION,
                    "test." + error,
                    "Invalid configuration"
                );
                default -> new IllegalArgumentException("unknown error");
            };
        }
    }
}
