package io.praporets.controlplane.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagConfigRepository extends JpaRepository<FlagConfig, UUID> {

    Optional<FlagConfig> findByFlagKeyAndEnvironmentKey(String flagKey, String environmentKey);
}
