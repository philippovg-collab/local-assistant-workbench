package com.example.demo.infrastructure.material;

import com.example.demo.service.MaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.materials.legacy-import-enabled",
    havingValue = "true"
)
public class LegacyMaterialImporter implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LegacyMaterialImporter.class);

    private final FileMaterialRepository fileMaterialRepository;
    private final MaterialService materialService;

    public LegacyMaterialImporter(
        com.fasterxml.jackson.databind.ObjectMapper objectMapper,
        @Value("${app.storage-dir:./storage}") String storageDir,
        MaterialService materialService
    ) {
        this(new FileMaterialRepository(objectMapper, storageDir), materialService);
    }

    public LegacyMaterialImporter(
        FileMaterialRepository fileMaterialRepository,
        MaterialService materialService
    ) {
        this.fileMaterialRepository = fileMaterialRepository;
        this.materialService = materialService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;

        for (StoredMaterialRecord record : fileMaterialRepository.findAll()) {
            try {
                if (materialService.importLegacyRecord(record)) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                logger.warn(
                    "Failed to import legacy material id={} title={} cause={}",
                    record.id(),
                    record.title(),
                    exception.getMessage(),
                    exception
                );
            }
        }

        logger.info(
            "Legacy material import completed: imported={} skipped={} failed={}",
            imported,
            skipped,
            failed
        );
    }
}
