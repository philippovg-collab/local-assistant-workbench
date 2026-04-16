package com.example.demo.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.infrastructure.instruction.FileInstructionRepository;
import com.example.demo.infrastructure.instruction.StoredInstructionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.storage-dir=${java.io.tmpdir}/rag-studio-instruction-controller-unexpected-test-${random.uuid}")
@AutoConfigureMockMvc
class InstructionControllerUnexpectedErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRequestIdForUnexpectedFailures() throws Exception {
        mockMvc.perform(post("/api/instructions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Broken",
                      "category": "system",
                      "content": "Should fail"
                    }
                    """))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal.unexpected_error"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        FileInstructionRepository fileInstructionRepository(ObjectMapper objectMapper) {
            return new FileInstructionRepository(objectMapper, System.getProperty("java.io.tmpdir")) {
                @Override
                public List<StoredInstructionRecord> findAll() {
                    return List.of();
                }

                @Override
                public Optional<StoredInstructionRecord> findById(String id) {
                    return Optional.empty();
                }

                @Override
                public void save(StoredInstructionRecord record) {
                    throw new IllegalStateException("boom");
                }
            };
        }
    }
}
