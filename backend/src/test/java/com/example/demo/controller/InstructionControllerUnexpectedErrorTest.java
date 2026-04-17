package com.example.demo.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.service.InstructionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstructionControllerUnexpectedErrorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InstructionService instructionService = org.mockito.Mockito.mock(InstructionService.class);
        doThrow(new IllegalStateException("boom"))
            .when(instructionService)
            .createInstruction(any());
        mockMvc = MockMvcBuilders
            .standaloneSetup(new InstructionController(instructionService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

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
}
