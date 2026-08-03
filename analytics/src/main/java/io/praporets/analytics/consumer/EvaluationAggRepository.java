package io.praporets.analytics.consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Доступ до схеми analytics через {@code JdbcClient} (G1) — жодного ORM:
 * логіка і Є цими двома SQL-ами.
 *
 * <p><b>Залежності для інжекту:</b> {@code JdbcClient} (бін дає
 * spring-boot-starter-jdbc).
 *
 * <p><b>{@code markProcessed} (твоя робота):</b>
 * {@code INSERT INTO processed_event (evaluation_id) VALUES (?) ON CONFLICT
 * DO NOTHING} → {@code update()} повертає кількість рядків: 1 = нова подія,
 * 0 = дублікат (A-04). Це вся ідемпотентність — атомарна, переживає
 * рестарти і DLT-перегравання.
 *
 * <p><b>{@code incrementAggregate} (твоя робота):</b> upsert:
 * <pre>
 * INSERT INTO evaluation_agg (environment, flag_key, variant_key,
 *                             window_start, eval_count, unique_users)
 * VALUES (?, ?, ?, ?, 1, 0)
 * ON CONFLICT (environment, flag_key, variant_key, window_start)
 * DO UPDATE SET eval_count = evaluation_agg.eval_count + 1
 * </pre>
 * {@code unique_users} = 0 — чесний TODO 03d-2 (потребує віконного стану).
 * {@code windowStart} передавай типізовано ({@code OffsetDateTime} з UTC або
 * {@code Timestamp.from(instant)}) — камінь #7.
 */
@Repository
public class EvaluationAggRepository {

    private final JdbcTemplate jdbcTemplate;

    public EvaluationAggRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Позначає подію обробленою.
     *
     * @return {@code true}, якщо подія нова; {@code false} — уже бачили
     */
    public boolean markProcessed(UUID evaluationId) {
        return jdbcTemplate.update("insert into processed_event (evaluation_id) values (?) on conflict do nothing", evaluationId) == 1;
    }

    /**
     * +1 до похвилинного агрегату (вставляє рядок вікна, якщо його ще нема).
     *
     * @param windowStart початок хвилинного вікна (уже обрізаний до хвилини)
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

    public boolean markUserSeen(String environment, String flagKey, String variantKey, Instant window, String userKeyHash) {
        return jdbcTemplate.update("""
                insert into evaluation_user (environment, flag_key, variant_key, window_start, user_key_hash)
                values (?, ?, ?, ?, ?) on conflict do nothing
            """, environment, flagKey, variantKey, window.atOffset(ZoneOffset.UTC), userKeyHash) == 1;
    }
}
