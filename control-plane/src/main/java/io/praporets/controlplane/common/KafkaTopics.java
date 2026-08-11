package io.praporets.controlplane.common;

/**
 * Канонічні імена Kafka-топіків платформи — єдине місце правди,
 * на яке посилаються і конфігурація топіків, і продюсери зі споживачами.
 */
public final class KafkaTopics {

    /**
     * Зміни конфігурації: CP (outbox relay) → усі репліки CP.
     * 3 партиції, compacted, ключ — environmentKey.
     */
    public static final String FLAG_CHANGES = "praporets.flag.changes.v1";

    public static final String FLAG_EVALUATIONS = "praporets.flag.evaluations.v1";

    public static final String FLAG_EVALUATIONS_DLT = "praporets.flag.evaluations.v1.dlt";

    private KafkaTopics() {
    }
}
