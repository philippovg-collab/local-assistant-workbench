package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestOverrides.class)
class InstructionControllerIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsInstructions() throws Exception {
        String response = mockMvc.perform(post("/api/instructions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Базовая роль",
                      "category": "system",
                      "content": "Отвечай кратко."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Базовая роль"))
            .andExpect(jsonPath("$.category").value("system"))
            .andExpect(jsonPath("$.content").value("Отвечай кратко."))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String instructionId = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response)
            .get("id")
            .asText();

        mockMvc.perform(get("/api/instructions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Базовая роль"))
            .andExpect(jsonPath("$[0].preview").value("Отвечай кратко."))
            .andExpect(jsonPath("$[0].content").doesNotExist());

        mockMvc.perform(get("/api/instructions/{id}", instructionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Базовая роль"))
            .andExpect(jsonPath("$.content").value("Отвечай кратко."));
    }

    @Test
    void updatesInstructionWithoutChangingId() throws Exception {
        String response = mockMvc.perform(post("/api/instructions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Базовая роль",
                      "category": "system",
                      "content": "Отвечай кратко."
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String instructionId = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response)
            .get("id")
            .asText();

        mockMvc.perform(put("/api/instructions/{id}", instructionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Обновлённая роль",
                      "category": "context",
                      "content": "Отвечай подробнее."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(instructionId))
            .andExpect(jsonPath("$.title").value("Обновлённая роль"))
            .andExpect(jsonPath("$.category").value("context"))
            .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(get("/api/instructions/{id}", instructionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(instructionId))
            .andExpect(jsonPath("$.title").value("Обновлённая роль"))
            .andExpect(jsonPath("$.content").value("Отвечай подробнее."));
    }
}
