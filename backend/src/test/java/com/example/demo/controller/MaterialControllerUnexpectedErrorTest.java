package com.example.demo.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.infrastructure.material.DocumentTextExtractor;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.storage-dir=${java.io.tmpdir}/rag-studio-material-controller-unexpected-test-${random.uuid}")
@AutoConfigureMockMvc
class MaterialControllerUnexpectedErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRequestIdForUnexpectedFailures() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "pricing.txt",
            "text/plain",
            "Тариф Премиум".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal.unexpected_error"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        DocumentTextExtractor documentTextExtractor() {
            return (originalFileName, mediaType, bytes) -> {
                throw new IllegalStateException("boom");
            };
        }
    }
}
