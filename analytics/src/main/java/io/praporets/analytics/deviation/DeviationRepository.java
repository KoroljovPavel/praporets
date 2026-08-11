package io.praporets.analytics.deviation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Rollout-лічильники одного закритого вікна з {@code evaluation_agg}.
 * Варіанти з нульовим rollout_count у вибірку не входять — «зниклі»
 * варіанти добудовує {@link DeviationChecker} з очікуваних ваг.
 */
@Repository
public class DeviationRepository {

    private final JdbcClient jdbcClient;

    public DeviationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Рядок вікна з ненульовим rollout-лічильником.
     */
    public record RolloutCount(String environment, String flagKey, String variantKey, long rolloutCount) {
    }

    /**
     * Усі рядки вікна {@code windowStart} з {@code rollout_count > 0}.
     */
    public List<RolloutCount> rolloutCounts(Instant windowStart) {
        return jdbcClient.sql("""
                    select environment, flag_key, variant_key, rollout_count
                    from evaluation_agg
                    where window_start = :window and rollout_count > 0
                """)
            .param("window", windowStart.atOffset(ZoneOffset.UTC))
            .query((rs, i) -> new RolloutCount(
                rs.getString("environment"),
                rs.getString("flag_key"),
                rs.getString("variant_key"),
                rs.getLong("rollout_count")
            ))
            .list();
    }
}
