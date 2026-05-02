package com.example.demo.infrastructure.instruction;

import com.example.demo.service.InstructionService;
import com.example.demo.service.instruction.port.StoredInstructionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.instructions.legacy-import-enabled",
    havingValue = "true"
)
public class LegacyInstructionImporter implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LegacyInstructionImporter.class);

    private final InstructionService instructionService;
    private final FileInstructionRepository fileInstructionRepository;

    public LegacyInstructionImporter(
        InstructionService instructionService,
        com.fasterxml.jackson.databind.ObjectMapper objectMapper,
        @Value("${app.storage-dir:./storage}") String storageDir
    ) {
        this.instructionService = instructionService;
        this.fileInstructionRepository = new FileInstructionRepository(objectMapper, storageDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        int imported = 0;
        int failed = 0;

        for (StoredInstructionRecord record : fileInstructionRepository.findAll()) {
            try {
                if (instructionService.importLegacyRecord(record)) {
                    imported++;
                }
            } catch (RuntimeException exception) {
                failed++;
                logger.warn(
                    "Failed to import legacy instruction id={} title={} cause={}",
                    record.id(),
                    record.title(),
                    exception.getMessage(),
                    exception
                );
            }
        }

        logger.info(
            "Legacy instruction import completed: imported={} failed={}",
            imported,
            failed
        );
    }
}
