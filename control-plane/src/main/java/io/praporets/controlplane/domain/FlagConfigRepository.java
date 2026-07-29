package io.praporets.controlplane.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, UUID> {

    Optional<FlagConfig> findByFlagKeyAndEnvironmentKey(String flagKey, String environmentKey);

    /**
     * Усі конфігурації середовища для gRPC-снапшота (02b). {@code @EntityGraph}
     * тягне flag і його variants одним запитом — мапінг N флагів у proto
     * інакше дав би N+1 по variants (те саме правило, що в
     * {@code FlagRepositoryQueryCountTest}).
     */
    @EntityGraph(attributePaths = {"flag", "flag.variants"})
    List<FlagConfig> findAllByEnvironmentKey(String environmentKey);
}
