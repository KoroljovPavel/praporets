package io.praporets.analytics.stats;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Read path (H2): один SELECT по готових агрегатах — нічого не
 * дообчислюється з подій.
 *
 * <p><b>Залежності для інжекту:</b> {@code JdbcClient} (або
 * {@code JdbcTemplate} — як у {@code EvaluationAggRepository}).
 *
 * <p><b>{@code findWindows} (твоя робота):</b>
 * <pre>
 * SELECT variant_key, window_start, eval_count, unique_users
 * FROM evaluation_agg
 * WHERE environment = ? AND flag_key = ?
 *   AND window_start &gt;= ? AND window_start &lt; ?   -- напіввідкритий (камінь #4)
 * ORDER BY window_start                              -- порядок точок (камінь #6)
 * </pre>
 * {@code window_start} з БД — {@code Timestamp} → {@code toInstant()}
 * (уроки таймзон із 03d-1).
 */
@Repository
public class StatsRepository {

    private final JdbcClient jdbcClient;

    public StatsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Рядок агрегату для збірки серій.
     */
    public record AggWindow(String variantKey, Instant windowStart, long evalCount, long uniqueUsers) {
    }

    /**
     * Вікна флага в діапазоні, впорядковані за часом.
     *
     * @param from інклюзивна межа
     * @param to   ексклюзивна межа
     */
    public List<AggWindow> findWindows(String environment, String flagKey, Instant from, Instant to) {
        return jdbcClient.sql("""
                    select variant_key, window_start, eval_count, unique_users
                    from evaluation_agg
                    where environment = :env and flag_key = :flag_key and window_start >= :from and window_start < :to
                    order by window_start
                """)
            .param("env", environment)
            .param("flag_key", flagKey)
            .param("from", from.atOffset(ZoneOffset.UTC))
            .param("to", to.atOffset(ZoneOffset.UTC))
            .query((rs, i) -> new AggWindow(
                rs.getString("variant_key"),
                rs.getTimestamp("window_start").toInstant(),
                rs.getLong("eval_count"),
                rs.getLong("unique_users")
            )).list();
    }
}
