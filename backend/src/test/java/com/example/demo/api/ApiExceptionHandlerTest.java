package com.example.demo.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.MaterialProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
            .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void mapsMalformedMultipartRequestsToBadRequest() throws Exception {
        mockMvc.perform(get("/test/errors/invalid-multipart"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.invalid_multipart"))
            .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void mapsMissingMultipartPartsToBadRequest() throws Exception {
        mockMvc.perform(get("/test/errors/missing-part"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.missing_file_part"))
            .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void mapsUnexpectedErrorsToInternalServerErrorWithRequestId() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal.unexpected_error"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
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
    }
}
