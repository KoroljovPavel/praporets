package io.praporets.analytics;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Singleton-Postgres на всю JVM тестового прогону — той самий патерн, що в
 * control-plane: контейнер переживає всі тест-класи, кешовані контексти не
 * лишаються з мертвим датасорсом; прибирає Ryuk. БД називається як на
 * стенді — {@code praporets_analytics}.
 */
public final class TestPostgres {

    public static final PostgreSQLContainer INSTANCE =
        new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("praporets_analytics")
            .withUsername("praporets")
            .withPassword("praporets");

    static {
        INSTANCE.start();
    }

    private TestPostgres() {
    }
}
