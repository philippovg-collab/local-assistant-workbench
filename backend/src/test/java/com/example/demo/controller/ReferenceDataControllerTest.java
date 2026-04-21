package com.example.demo.controller;

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
import com.example.demo.model.ReferenceProject;
import com.example.demo.model.ReferenceProjectRequest;
import com.example.demo.model.ReferenceWorkspace;
import com.example.demo.model.ReferenceWorkspaceRequest;
import com.example.demo.service.ReferenceDataService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReferenceDataControllerTest {

    private MockMvc mockMvc;
    private ReferenceDataService referenceDataService;

    @BeforeEach
    void setUp() {
        referenceDataService = mock(ReferenceDataService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReferenceDataController(referenceDataService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void listsWorkspacesWithActiveOnlyQueryParam() throws Exception {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(referenceDataService.listWorkspaces(false)).thenReturn(List.of(new ReferenceWorkspace(
            "general",
            "Общая",
            null,
            true,
            0,
            true,
            now,
            now
        )));

        mockMvc.perform(get("/api/reference/workspaces").param("activeOnly", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("general"))
            .andExpect(jsonPath("$[0].nameRu").value("Общая"))
            .andExpect(jsonPath("$[0].isDefault").value(true));

        verify(referenceDataService).listWorkspaces(false);
    }

    @Test
    void createsWorkspaceFromJsonPayload() throws Exception {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(referenceDataService.createWorkspace(org.mockito.ArgumentMatchers.any())).thenReturn(new ReferenceWorkspace(
            "north-upgrade",
            "Северная модернизация",
            "Контур северной модернизации",
            true,
            10,
            false,
            now,
            now
        ));

        mockMvc.perform(post("/api/reference/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "key": "north-upgrade",
                      "nameRu": "Северная модернизация",
                      "description": "Контур северной модернизации",
                      "active": true,
                      "sortOrder": 10,
                      "isDefault": false
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value("north-upgrade"))
            .andExpect(jsonPath("$.isDefault").value(false));

        ArgumentCaptor<ReferenceWorkspaceRequest> requestCaptor = ArgumentCaptor.forClass(ReferenceWorkspaceRequest.class);
        verify(referenceDataService).createWorkspace(requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("north-upgrade", requestCaptor.getValue().key());
        org.junit.jupiter.api.Assertions.assertEquals("Северная модернизация", requestCaptor.getValue().nameRu());
        org.junit.jupiter.api.Assertions.assertEquals("Контур северной модернизации", requestCaptor.getValue().description());
    }

    @Test
    void listsProjectsWithWorkspaceFilter() throws Exception {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(referenceDataService.listProjects(false, "north-upgrade")).thenReturn(List.of(new ReferenceProject(
            "north-grid",
            "north-upgrade",
            "Северная сеть",
            true,
            0,
            now,
            now
        )));

        mockMvc.perform(get("/api/reference/projects")
                .param("activeOnly", "false")
                .param("workspaceKey", "north-upgrade"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("north-grid"))
            .andExpect(jsonPath("$[0].workspaceKey").value("north-upgrade"));

        verify(referenceDataService).listProjects(false, "north-upgrade");
    }

    @Test
    void updatesProjectFromJsonPayload() throws Exception {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(referenceDataService.updateProject(org.mockito.ArgumentMatchers.eq("north-grid"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ReferenceProject(
                "north-grid",
                "north-upgrade",
                "Северная сеть 2",
                false,
                20,
                now,
                now
            ));

        mockMvc.perform(put("/api/reference/projects/north-grid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceKey": "north-upgrade",
                      "nameRu": "Северная сеть 2",
                      "active": false,
                      "sortOrder": 20
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("north-grid"))
            .andExpect(jsonPath("$.active").value(false));

        ArgumentCaptor<ReferenceProjectRequest> requestCaptor = ArgumentCaptor.forClass(ReferenceProjectRequest.class);
        verify(referenceDataService).updateProject(org.mockito.ArgumentMatchers.eq("north-grid"), requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("north-upgrade", requestCaptor.getValue().workspaceKey());
        org.junit.jupiter.api.Assertions.assertEquals("Северная сеть 2", requestCaptor.getValue().nameRu());
    }
}
