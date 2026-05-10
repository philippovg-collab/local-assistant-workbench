package com.example.demo.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.RagProjectRequest;
import com.example.demo.model.RagProjectSummary;
import com.example.demo.service.RagProjectService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagProjectControllerContractTest {

    private MockMvc mockMvc;
    private RagProjectService ragProjectService;

    @BeforeEach
    void setUp() {
        ragProjectService = mock(RagProjectService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new RagProjectController(ragProjectService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void listsProjectsWithCountsAndActiveOnlyQueryParam() throws Exception {
        when(ragProjectService.listProjects(false)).thenReturn(List.of(new RagProjectSummary(
            "north-rag",
            "Северный RAG",
            "Корпус северного проекта",
            true,
            false,
            10,
            12,
            8,
            Instant.parse("2026-04-20T10:00:00Z")
        )));

        mockMvc.perform(get("/api/rag-projects").param("activeOnly", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("north-rag"))
            .andExpect(jsonPath("$[0].name").value("Северный RAG"))
            .andExpect(jsonPath("$[0].description").value("Корпус северного проекта"))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].isDefault").value(false))
            .andExpect(jsonPath("$[0].sortOrder").value(10))
            .andExpect(jsonPath("$[0].materialCount").value(12))
            .andExpect(jsonPath("$[0].readyMaterialCount").value(8))
            .andExpect(jsonPath("$[0].updatedAt").exists());

        verify(ragProjectService).listProjects(false);
    }

    @Test
    void createsProjectFromJsonPayload() throws Exception {
        when(ragProjectService.createProject(any())).thenReturn(new RagProjectSummary(
            "south-rag",
            "Южный RAG",
            "Корпус южного проекта",
            true,
            true,
            5,
            0,
            0,
            Instant.parse("2026-04-21T10:00:00Z")
        ));
        ArgumentCaptor<RagProjectRequest> requestCaptor = ArgumentCaptor.forClass(RagProjectRequest.class);

        mockMvc.perform(post("/api/rag-projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "key": "south-rag",
                      "name": "Южный RAG",
                      "description": "Корпус южного проекта",
                      "active": true,
                      "sortOrder": 5,
                      "isDefault": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value("south-rag"))
            .andExpect(jsonPath("$.isDefault").value(true))
            .andExpect(jsonPath("$.materialCount").value(0))
            .andExpect(jsonPath("$.readyMaterialCount").value(0));

        verify(ragProjectService).createProject(requestCaptor.capture());
        assertEquals("south-rag", requestCaptor.getValue().key());
        assertEquals("Южный RAG", requestCaptor.getValue().name());
    }

    @Test
    void updatesProjectFromJsonPayload() throws Exception {
        when(ragProjectService.updateProject(eq("south-rag"), any())).thenReturn(new RagProjectSummary(
            "south-rag",
            "Южный RAG v2",
            "Обновленный корпус",
            false,
            false,
            15,
            4,
            1,
            Instant.parse("2026-04-22T10:00:00Z")
        ));
        ArgumentCaptor<RagProjectRequest> requestCaptor = ArgumentCaptor.forClass(RagProjectRequest.class);

        mockMvc.perform(put("/api/rag-projects/south-rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Южный RAG v2",
                      "description": "Обновленный корпус",
                      "active": false,
                      "sortOrder": 15,
                      "isDefault": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("south-rag"))
            .andExpect(jsonPath("$.name").value("Южный RAG v2"))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.materialCount").value(4))
            .andExpect(jsonPath("$.readyMaterialCount").value(1));

        verify(ragProjectService).updateProject(eq("south-rag"), requestCaptor.capture());
        assertNull(requestCaptor.getValue().key());
        assertEquals("Южный RAG v2", requestCaptor.getValue().name());
    }
}
