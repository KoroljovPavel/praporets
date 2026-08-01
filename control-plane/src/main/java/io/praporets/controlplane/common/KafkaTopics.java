package io.praporets.controlplane.common;

/**
 * Канонічні імена Kafka-топіків (спека §6.4). ГОТОВИЙ клас-контракт —
 * імена пінятся тестами і конфігурацією топіка.
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
