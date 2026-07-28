package io.praporets.controlplane.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlagRepository extends JpaRepository<Flag, UUID> {

    Optional<Flag> findByKey(String key);

    /**
     * Флаг разом із варіантами — <b>одним</b> SQL-запитом (P4).
     *
     * <p>Поточна заглушка вантажить лише флаг: доступ до {@code variants} дасть
     * другий запит, і {@code FlagRepositoryQueryCountTest} червоний. Твоя робота —
     * зробити один запит: {@code join fetch} у запиті або {@code @EntityGraph}.
     */
    @Query("select f from Flag f where f.key = :key")
    Optional<Flag> findByKeyWithVariants(@Param("key") String key);
}
