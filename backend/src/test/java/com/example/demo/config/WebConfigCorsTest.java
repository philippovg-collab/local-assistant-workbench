package com.example.demo.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.controller.HealthController;
import com.example.demo.service.HealthStatusService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ WebConfig.class, WebConfigCorsTest.TestConfig.class })
class WebConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService healthStatusService;

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:5173",
        "http://localhost:5173"
    })
    void allowsConfiguredOrigins(String origin) throws Exception {
        mockMvc.perform(options("/api/health")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        MaterialProperties materialProperties() {
            return new MaterialProperties();
        }

        @Bean
        RequestLimitProperties requestLimitProperties() {
            return new RequestLimitProperties();
        }
    }
}
