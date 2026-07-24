package com.magentamause.cosybackend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Drift guard for the Flyway-managed schema, run only in CI against a real Postgres.
 *
 * <p>Booting the Spring context with Flyway enabled and {@code ddl-auto: validate} proves that
 * Flyway migrated an empty Postgres and that Hibernate's schema validation passed against the
 * migrated result — i.e. the entities and the migrations agree. The explicit assertions then pin
 * down that both migrations ran and that V2 actually widened the column.
 *
 * <p>Gated behind {@code CI_PG_TESTS=true} so it never runs on a developer machine by accident.
 */
@SpringBootTest
@ActiveProfiles("ci-pg")
@EnabledIfEnvironmentVariable(named = "CI_PG_TESTS", matches = "true")
class FlywayMigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigratedSchemaAndValidatePassed() {
        // If the context booted, Flyway migrated the empty DB and Hibernate `validate` passed.

        Integer successfulMigrations =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                        Integer.class);
        assertThat(successfulMigrations).isNotNull().isGreaterThanOrEqualTo(2);

        String descriptionType =
                jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns"
                                + " WHERE table_name = 'template_entity'"
                                + " AND column_name = 'description'",
                        String.class);
        assertThat(descriptionType).isEqualTo("text");
    }
}
