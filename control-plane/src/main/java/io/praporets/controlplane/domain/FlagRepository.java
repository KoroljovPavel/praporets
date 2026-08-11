package io.praporets.controlplane.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FlagRepository extends JpaRepository<Flag, UUID> {

    Optional<Flag> findByKey(String key);

    /**
     * Флаг разом із варіантами — <b>одним</b> SQL-запитом:
     * {@code @EntityGraph} перетворює доступ до {@code variants} на join
     * замість другого запиту (це пінить {@code FlagRepositoryQueryCountTest}).
     */
    @Query("select f from Flag f where f.key = :key")
    @EntityGraph(attributePaths = "variants")
    Optional<Flag> findByKeyWithVariants(@Param("key") String key);

    Page<Flag> findAllByArchived(boolean archived, Pageable pageable);
}
