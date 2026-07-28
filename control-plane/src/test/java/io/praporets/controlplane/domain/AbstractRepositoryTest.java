package io.praporets.controlplane.domain;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Спільна база repo-тестів: слайс {@code @DataJpaTest} (без web/gRPC-частин
 * контексту) + реальний Postgres із Testcontainers + статистика Hibernate
 * для query-count тестів (у проді statistics не вмикаємо — це тест-конфіг).
 * Кожен тест відкатується — база між тестами чиста.
 *
 * <p>Контейнер — singleton: старт у static-ініціалізаторі, БЕЗ
 * {@code @Testcontainers}/{@code @Container}. Розширення JUnit зупиняє
 * static-контейнер після кожного тест-класу, а Spring кешує ApplicationContext
 * (і зафіксований у ньому JDBC URL) на всі класи-нащадки — другий клас
 * отримав би контекст, що дивиться на мертвий порт. Singleton живе до кінця
 * JVM; прибирає його Ryuk.
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.hibernate.ddl-auto=validate",
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
