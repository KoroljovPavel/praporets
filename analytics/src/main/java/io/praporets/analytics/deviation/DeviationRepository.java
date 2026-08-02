package io.praporets.analytics.deviation;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Rollout-лічильники одного закритого вікна (I3).
 *
 * <p><b>Залежності для інжекту:</b> {@code JdbcClient}.
 *
 * <p><b>{@code rolloutCounts} (твоя робота):</b>
 * <pre>
 * SELECT environment, flag_key, variant_key, rollout_count
 * FROM evaluation_agg
 * WHERE window_start = :window AND rollout_count &gt; 0
 * </pre>
 * ({@code window} — {@code atOffset(UTC)}, уроки 03d-1/2). Варіанти з
 * нульовим rollout_count у вибірку не входять — «зниклі» варіанти
 * добудовує {@link DeviationChecker} з очікувань (I5).
 */
@Repository
public class DeviationRepository {

    /**
     * Рядок вікна з ненульовим rollout-лічильником.
     */
    public record RolloutCount(String environment, String flagKey, String variantKey, long rolloutCount) {
    }

    public List<RolloutCount> rolloutCounts(Instant windowStart) {
        throw new UnsupportedOperationException("03d-3: твоя реалізація");
    }
}
