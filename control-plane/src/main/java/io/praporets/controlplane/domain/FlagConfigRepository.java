package io.praporets.controlplane.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, UUID> {

    Optional<FlagConfig> findByFlagKeyAndEnvironmentKey(String flagKey, String environmentKey);

    /**
     * Усі конфігурації середовища для gRPC-снапшота. {@code @EntityGraph}
     * тягне flag і його variants одним запитом — мапінг N флагів у proto
     * інакше дав би N+1 по variants.
     */
    @EntityGraph(attributePaths = {"flag", "flag.variants"})
    List<FlagConfig> findAllByEnvironmentKey(String environmentKey);
}
