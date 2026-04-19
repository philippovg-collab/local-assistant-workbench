package com.example.demo.controller;

import com.example.demo.model.HealthResponse;
import com.example.demo.service.HealthStatusService;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthStatusService healthStatusService;

    public HealthController(HealthStatusService healthStatusService) {
        this.healthStatusService = healthStatusService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return healthStatusService.currentHealth();
    }

    @GetMapping("/liveness")
    public Map<String, String> liveness() {
        return Map.of(
            "application", "spring-backend",
            "status", "UP",
            "timestamp", Instant.now().toString()
        );
    }
}
