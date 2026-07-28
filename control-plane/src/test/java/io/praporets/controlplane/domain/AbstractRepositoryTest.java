package io.praporets.controlplane.domain;

import io.praporets.controlplane.TestPostgres;
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
 * <p>Контейнер — спільний singleton {@link TestPostgres} (чому саме так —
 * дивись JavaDoc там).
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.hibernate.ddl-auto=validate",
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;
}
