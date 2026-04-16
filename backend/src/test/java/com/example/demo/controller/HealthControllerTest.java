package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.storage-dir=${java.io.tmpdir}/rag-studio-health-controller-test-${random.uuid}")
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestOcrCapabilityProvider ocrCapabilityProvider;

    @BeforeEach
    void resetCapability() {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12));
    }

    @Test
    void reportsUpWhenOcrReadinessIsHealthy() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.ocrStatus").value("UP"))
            .andExpect(jsonPath("$.ocrLanguages[0]").value("kaz"));
    }

    @Test
    void reportsDegradedWhenOcrReadinessFails() throws Exception {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextOnly(
            "material.ocr_unavailable",
            "Tesseract OCR binary is unavailable at 'tesseract'.",
            List.of("kaz", "rus", "eng"),
            12
        ));

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.ocrStatus").value("DOWN"))
            .andExpect(jsonPath("$.ocrReasonCode").value("material.ocr_unavailable"))
            .andExpect(jsonPath("$.ocrReasonMessage").value("Tesseract OCR binary is unavailable at 'tesseract'."));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        TestOcrCapabilityProvider testOcrCapabilityProvider() {
            return new TestOcrCapabilityProvider();
        }
    }

    static class TestOcrCapabilityProvider implements OcrCapabilityProvider {

        private volatile OcrCapability capability = OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12);

        void setCapability(OcrCapability capability) {
            this.capability = capability;
        }

        @Override
        public OcrCapability currentCapability() {
            return capability;
        }
    }
}
