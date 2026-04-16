package com.example.demo.infrastructure.material;

import java.io.IOException;

public interface TesseractRuntimeProbe {

    CommandResult listLanguages(String binaryPath, int timeoutSeconds) throws IOException, InterruptedException;

    record CommandResult(
        int exitCode,
        String standardOutput,
        String standardError,
        boolean timedOut
    ) {
    }
}
