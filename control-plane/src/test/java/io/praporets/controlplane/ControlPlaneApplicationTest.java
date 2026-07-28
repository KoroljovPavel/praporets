package io.praporets.controlplane;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ControlPlaneApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void context_loads_and_flyway_applies_schema() {
        Integer applied = jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class
        );
        assertThat(applied).isEqualTo(1);

        assertThat(jdbc.queryForList("""
            select table_name from information_schema.tables
            where table_schema = 'public' and table_name != 'flyway_schema_history'
            """, String.class))
            .containsExactlyInAnyOrder("environment", "flag", "variant", "segment",
                "flag_config", "revision_log", "audit_log");
    }
}
