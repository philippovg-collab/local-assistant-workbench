package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.storage-dir=${java.io.tmpdir}/rag-studio-instruction-controller-test-${random.uuid}")
@AutoConfigureMockMvc
class InstructionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsInstructions() throws Exception {
        mockMvc.perform(post("/api/instructions")
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
            .andExpect(jsonPath("$.category").value("system"));

        mockMvc.perform(get("/api/instructions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Базовая роль"))
            .andExpect(jsonPath("$[0].content").value("Отвечай кратко."));
    }
}
