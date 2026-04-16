package com.example.demo.infrastructure.material;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessTesseractRuntimeProbe implements TesseractRuntimeProbe {

    @Override
    public CommandResult listLanguages(String binaryPath, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(List.of(binaryPath, "--list-langs")).start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new CommandResult(-1, "", "Capability check timed out", true);
        }

        return new CommandResult(
            process.exitValue(),
            new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
            new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8),
            false
        );
    }
}
