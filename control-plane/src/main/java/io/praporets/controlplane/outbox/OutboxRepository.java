package io.praporets.controlplane.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Доступ до outbox. Серце — {@link #lockNextBatch}: черга на Postgres
 * без брокера в транзакції.
 *
 * <p>Додай {@code extends JpaRepository<OutboxEntry, UUID>} разом з ентіті
 * (скелет лишений без extends свідомо: порожній OutboxEntry ще не @Entity,
 * і Spring Data валив би контекст усім існуючим тестам).
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /**
     * Захоплює наступну пачку неопублікованих подій.
     *
     * <p><b>Твій запит (native! JPQL не знає FOR UPDATE SKIP LOCKED):</b>
     * найстаріші {@code limit} рядків із {@code published_at IS NULL},
     * {@code ORDER BY created_at}, {@code FOR UPDATE SKIP LOCKED}.
     * Семантика: рядки, вже залоковані іншим relay (інша репліка CP),
     * мовчки пропускаються — два relay ділять чергу без блокувань і без
     * подвійної відправки в межах здорових транзакцій.
     *
     * <p>Викликати ТІЛЬКИ всередині транзакції — лок живе до її кінця
     * (камінь #4).
     *
     * @param limit розмір пачки ({@code praporets.outbox.relay.batch-size})
     * @return найстаріші незалоковані неопубліковані події, старіші перші
     */
    @NativeQuery(value = """
            select * from outbox o
            where o.published_at is null
            order by created_at
            limit :limit
            for update skip locked
        """)
    List<OutboxEntry> lockNextBatch(@Param("limit") int limit);

    /**
     * Час створення найстарішої неопублікованої події — паливо для гейджа
     * {@code praporets_outbox_lag_seconds} (K6). Порожньо = лаг 0.
     */
    @NativeQuery(value = """
            select o.created_at from outbox o
            where published_at is null
            order by created_at limit 1
        """)
    Optional<Instant> findOldestUnpublishedCreatedAt();
}
