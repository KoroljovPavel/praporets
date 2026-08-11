package io.praporets.controlplane.domain;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Оптимістичний лок: {@code @Version} захищає від lost update. Конкурент емулюється прямим UPDATE
 * повз persistence context — детерміновано, без потоків і race'ів у тесті.
 */
class OptimisticLockingTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void version_starts_at_zero_and_increments_on_update() {
        var flag = em.persistFlushFind(new Flag("pricing.exp", "Pricing", null, ValueType.STRING));
        assertThat(flag.getVersion()).isZero();

        flag.setName("Pricing v2");
        em.flush();

        assertThat(flag.getVersion()).isEqualTo(1);
    }

    @Test
    void stale_write_is_rejected() {
        var flag = em.persistFlushFind(new Flag("pricing.exp", "Pricing", null, ValueType.STRING));

        // «інший оператор» устиг зберегти свою зміну
        jdbc.update("update flag set version = version + 1 where id = ?", flag.getId());

        flag.setName("stale write");

        assertThatThrownBy(em::flush)
                .isInstanceOfAny(OptimisticLockException.class,
                        ObjectOptimisticLockingFailureException.class);
    }
}
