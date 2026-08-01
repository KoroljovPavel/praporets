package io.praporets.analytics.consumer;

import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /**
     * Позначає подію обробленою.
     *
     * @return {@code true}, якщо подія нова; {@code false} — уже бачили
     */
    public boolean markProcessed(UUID evaluationId) {
        throw new UnsupportedOperationException("03d-1: твоя реалізація");
    }

    /**
     * +1 до похвилинного агрегату (вставляє рядок вікна, якщо його ще нема).
     *
     * @param windowStart початок хвилинного вікна (уже обрізаний до хвилини)
     */
    public void incrementAggregate(String environment, String flagKey, String variantKey, Instant windowStart) {
        throw new UnsupportedOperationException("03d-1: твоя реалізація");
    }
}
