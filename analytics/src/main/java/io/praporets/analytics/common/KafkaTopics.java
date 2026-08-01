package io.praporets.analytics.common;

/**
 * Канонічні імена Kafka-топіків (спека §6.4) — власна копія analytics:
 * модулі незалежні, спільного «common»-артефакту свідомо немає (це
 * імена-контракти, а не код). Декларує топіки control-plane (03c).
 */
public final class KafkaTopics {

    /**
     * Потік evaluation-подій: flag-edge → analytics. Ключ — flagKey.
     */
    public static final String FLAG_EVALUATIONS = "praporets.flag.evaluations.v1";

    /**
     * Отруйні повідомлення після вичерпання ретраїв (G5).
     */
    public static final String FLAG_EVALUATIONS_DLT = "praporets.flag.evaluations.v1.dlt";

    private KafkaTopics() {
    }
}
