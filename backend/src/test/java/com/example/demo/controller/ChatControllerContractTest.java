package com.example.demo.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.service.ChatExecutionService;
import com.example.demo.service.InstructionService;
import com.example.demo.service.ModelCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

class ChatControllerContractTest {

    private MockMvc mockMvc;
    private ChatExecutionService chatExecutionService;
    private InstructionService instructionService;
    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        chatExecutionService = mock(ChatExecutionService.class);
        instructionService = mock(InstructionService.class);
        llmClient = mock(LlmClient.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatController(
                chatExecutionService,
                instructionService,
                new ModelCatalogService(llmClient)
            ))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void rejectsMalformedChatPayloadsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(chatExecutionService);
    }

    @Test
    void rejectsInvalidModeValuesWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "broken",
                      "model": "qwen2.5:7b",
                      "prompt": "Сколько стоит тариф Премиум?",
                      "instructionIds": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(chatExecutionService);
    }
}
