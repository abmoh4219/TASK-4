package com.registrarops.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for every Spring-context integration test.
 *
 * Two backends are supported, picked at startup:
 *
 *  1. **External MySQL** (preferred when running inside Docker Compose).
 *     If the environment exposes {@code TEST_DATASOURCE_URL} (or
 *     {@code SPRING_DATASOURCE_URL}), {@code TEST_DATASOURCE_USERNAME},
 *     {@code TEST_DATASOURCE_PASSWORD}, we use that database directly.
 *     This is the path used by {@code docker compose --profile test run test}
 *     where a sibling {@code mysql} service is already healthy on the same
 *     compose network — no Docker-in-Docker, no Testcontainers networking.
 *
 *  2. **Testcontainers MySQL 8** (default for local developer machines).
 *     If no env var is set we spin up a real MySQL 8 container via
 *     Testcontainers (NOT H2 — never H2). Flyway runs all migrations at
 *     context startup so each test class sees the full schema and seed data
 *     exactly as the production app does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    @Autowired(required = false)
    private Flyway flyway;

    /**
     * Reset the database before every test method when we're talking to an
     * external (shared) MySQL. This drops every Flyway-managed table and
     * re-applies V001..V013 so the seed data is fresh and tests can't
     * pollute each other.
     *
     * When the in-memory {@link MySQLContainer} is in use, Testcontainers
     * already gives every test class its own database, so we skip the reset.
     */
    @BeforeEach
    void resetSchema() {
        if (USE_EXTERNAL && flyway != null) {
            flyway.clean();
            flyway.migrate();
        }
    }


    private static final String EXTERNAL_URL =
            firstNonEmpty(System.getenv("TEST_DATASOURCE_URL"),
                          System.getenv("SPRING_DATASOURCE_URL"));
    private static final String EXTERNAL_USER =
            firstNonEmpty(System.getenv("TEST_DATASOURCE_USERNAME"),
                          System.getenv("SPRING_DATASOURCE_USERNAME"),
                          "registrar");
    private static final String EXTERNAL_PASS =
            firstNonEmpty(System.getenv("TEST_DATASOURCE_PASSWORD"),
                          System.getenv("SPRING_DATASOURCE_PASSWORD"),
                          "registrar_pass");

    private static final boolean USE_EXTERNAL = EXTERNAL_URL != null;

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL =
            USE_EXTERNAL ? null : new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("registrarops")
                    .withUsername("registrar")
                    .withPassword("registrar_pass");

    static {
        if (!USE_EXTERNAL) {
            MYSQL.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (MYSQL.isRunning()) MYSQL.stop();
            }));
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL) {
            registry.add("spring.datasource.url",      () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> EXTERNAL_USER);
            registry.add("spring.datasource.password", () -> EXTERNAL_PASS);
        } else {
            registry.add("spring.datasource.url",      MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled",       () -> "true");
        // Always start each test class with a clean schema so DirtiesContext
        // teardown of one class doesn't leak rows into the next.
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    @TestConfiguration
    static class TestConfig { }
}
