package io.praporets.controlplane;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * База інтеграційних тестів 01g: повний контекст + MockMvc + той самий
 * singleton-Postgres, що й у repo-тестів ({@link TestPostgres}).
 *
 * <p>{@code @Transactional} відкочує кожен тест; сервісні
 * {@code @Transactional}-методи джойняться до тест-транзакції, тож навіть
 * записи в revision_log/audit_log не переживають тест — база чиста.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
