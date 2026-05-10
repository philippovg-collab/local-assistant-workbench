package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ServiceLayerBoundaryTest {

    @Test
    void architectureBoundaryGatePasses() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("python3", "../scripts/architecture-boundary-gate.py")
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), output);
    }
}
