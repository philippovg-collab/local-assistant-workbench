package com.example.demo.support;

import org.flywaydb.core.Flyway;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class PostgresIntegrationTestSupport {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("ragstudio_test")
        .withUsername("ragstudio")
        .withPassword("ragstudio");

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.storage-dir", () -> Path.of(
            System.getProperty("java.io.tmpdir"),
            "rag-studio-test-storage-" + UUID.randomUUID()
        ).toString());
        registry.add("app.materials.legacy-import-enabled", () -> "false");
        registry.add("app.instructions.legacy-import-enabled", () -> "false");
    }

    protected static String postgresJdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String postgresUsername() {
        return POSTGRES.getUsername();
    }

    protected static String postgresPassword() {
        return POSTGRES.getPassword();
    }

    protected TestDatabase resetDatabase() {
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
        return new TestDatabase(
            dataSource,
            new JdbcTemplate(dataSource),
            new DataSourceTransactionManager(dataSource)
        );
    }

    protected record TestDatabase(
        DriverManagerDataSource dataSource,
        JdbcTemplate jdbcTemplate,
        DataSourceTransactionManager transactionManager
    ) {
    }
}
