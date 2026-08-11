package io.praporets.controlplane.domain;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Анти-N+1: агрегат «флаг + варіанти» вантажиться рівно одним SQL-запитом.
 * Ловить регресію автоматично — замість «когось колись здивує повільний ендпоінт».
 * Один із трьох тестів, на які посилатиметься README.
 */
class FlagRepositoryQueryCountTest extends AbstractRepositoryTest {

    @Autowired
    FlagRepository flags;

    @Autowired
    TestEntityManager em;

    @Autowired
    EntityManagerFactory emf;

    @Test
    void loading_flag_with_variants_issues_single_query() {
        var flag = new Flag("checkout.new-flow", "New checkout", null, ValueType.BOOLEAN);
        flag.addVariant(new Variant("on", "true"));
        flag.addVariant(new Variant("off", "false"));
        em.persistAndFlush(flag);
        em.clear();   // холодний старт: у persistence context нічого немає

        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var loaded = flags.findByKeyWithVariants("checkout.new-flow").orElseThrow();

        // торкаємось колекції ДО перевірки лічильника — ліниве довантаження було б другим запитом
        assertThat(loaded.getVariants()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount())
                .as("flag + variants мають прийти одним запитом (fetch join / @EntityGraph)")
                .isEqualTo(1);
    }
}
