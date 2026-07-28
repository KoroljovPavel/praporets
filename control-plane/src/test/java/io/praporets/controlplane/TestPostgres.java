package io.praporets.controlplane;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Єдиний Postgres-контейнер на всю JVM тестового прогону.
 *
 * <p>Свідомо БЕЗ {@code @Testcontainers}/{@code @Container}: JUnit-розширення
 * зупиняє static-контейнер після кожного тест-класу, а Spring кешує
 * ApplicationContext (і JDBC URL у ньому) на всі класи з однаковою
 * конфігурацією — наступний клас отримав би контекст на мертвий порт.
 * Singleton живе до кінця JVM; контейнер прибирає Ryuk.
 */
public final class TestPostgres {

    public static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer("postgres:17-alpine");

    static {
        INSTANCE.start();
    }

    private TestPostgres() {
    }
}
