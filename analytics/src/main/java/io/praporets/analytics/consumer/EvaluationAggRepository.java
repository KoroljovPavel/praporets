package io.praporets.analytics.consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Доступ до схеми analytics через {@code JdbcTemplate} — жодного ORM:
 * логіка модуля і є цими кількома SQL-ами. Уся ідемпотентність конвеєра
 * тримається на {@code ON CONFLICT DO NOTHING}: атомарно, переживає
 * рестарти консюмера і перегравання з DLT. Часові параметри передаються
 * типізовано ({@code OffsetDateTime} в UTC), не рядками — інакше
 * порівняння вікон залежало б від таймзони сесії.
 */
@Repository
public class EvaluationAggRepository {

    private final JdbcTemplate jdbcTemplate;

    public EvaluationAggRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Позначає подію обробленою ({@code INSERT ... ON CONFLICT DO NOTHING}
     * у {@code processed_event}): кількість вставлених рядків і є відповіддю.
     *
     * @return {@code true}, якщо подія нова; {@code false} — уже бачили
     */
    public boolean markProcessed(UUID evaluationId) {
        return jdbcTemplate.update("insert into processed_event (evaluation_id) values (?) on conflict do nothing", evaluationId) == 1;
    }

    /**
     * +1 до похвилинного агрегату (upsert: вставляє рядок вікна, якщо його
     * ще нема, інакше інкрементить лічильники).
     *
     * @param windowStart  початок хвилинного вікна (уже обрізаний до хвилини)
     * @param uniqueDelta  1, якщо користувач у цьому вікні новий, інакше 0
     * @param rolloutDelta 1, якщо подія — flag-level rollout, інакше 0
     */
    public void incrementAggregate(String environment, String flagKey, String variantKey, Instant windowStart, long uniqueDelta, long rolloutDelta) {
        jdbcTemplate.update("""
            insert into evaluation_agg (environment, flag_key, variant_key, window_start, eval_count, unique_users, rollout_count) values (?, ?, ?, ?, 1, ?, ?)
            on conflict (environment, flag_key, variant_key, window_start)
            do update set
                          eval_count = evaluation_agg.eval_count + 1,
                          unique_users = evaluation_agg.unique_users + excluded.unique_users,
                          rollout_count = evaluation_agg.rollout_count + excluded.rollout_count;
            """, environment, flagKey, variantKey, windowStart.atOffset(ZoneOffset.UTC), uniqueDelta, rolloutDelta);
    }

    /**
     * Реєструє користувача у вікні (таблиця {@code evaluation_user}).
     *
     * @return {@code true}, якщо користувач у цьому вікні вперше
     */
    public boolean markUserSeen(String environment, String flagKey, String variantKey, Instant window, String userKeyHash) {
        return jdbcTemplate.update("""
                insert into evaluation_user (environment, flag_key, variant_key, window_start, user_key_hash)
                values (?, ?, ?, ?, ?) on conflict do nothing
            """, environment, flagKey, variantKey, window.atOffset(ZoneOffset.UTC), userKeyHash) == 1;
    }
}
