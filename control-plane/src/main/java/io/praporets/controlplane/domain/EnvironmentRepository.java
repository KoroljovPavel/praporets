package io.praporets.controlplane.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    Optional<Environment> findByKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Environment> findWithLockByKey(String key);
}
