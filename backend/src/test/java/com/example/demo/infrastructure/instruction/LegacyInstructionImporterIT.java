package com.example.demo.infrastructure.instruction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.service.InstructionService;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LegacyInstructionImporterIT extends PostgresIntegrationTestSupport {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway flyway = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();
        flyway.clean();
        flyway.migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void importsLegacyInstructionFileStorageIntoPostgres() throws Exception {
        Path storageDir = Files.createTempDirectory("legacy-instruction-import");
        Files.createDirectories(storageDir.resolve("instructions"));
        Files.writeString(
            storageDir.resolve("instructions").resolve("stay-concise.json"),
            """
                {
                  "id":"12a6d267-55b0-47da-80e4-b9e0bcac2df2",
                  "title":"Stay concise",
                  "category":"system",
                  "content":"Отвечай кратко и по делу.",
                  "createdAt":"2026-04-15T16:12:34.078741Z",
                  "updatedAt":"2026-04-15T16:12:34.078742Z"
                }
                """
        );

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        InstructionService instructionService = new InstructionService(
            new PostgresInstructionRepository(new JdbcTemplate(dataSource))
        );
        LegacyInstructionImporter importer = new LegacyInstructionImporter(
            instructionService,
            objectMapper,
            storageDir.toString()
        );

        importer.run(new DefaultApplicationArguments(new String[0]));

        Integer instructionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instructions", Integer.class);
        String normalizedContent = jdbcTemplate.queryForObject(
            "SELECT normalized_content FROM instructions LIMIT 1",
            String.class
        );

        assertEquals(1, instructionCount);
        assertEquals("Отвечай кратко и по делу.", normalizedContent);
    }
}
