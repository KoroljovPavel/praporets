package io.praporets.analytics.common;

/**
 * Канонічні імена Kafka-топіків — власна копія analytics:
 * модулі незалежні, спільного «common»-артефакту свідомо немає (це
 * імена-контракти, а не код). Топіки декларує control-plane.
 */
public final class KafkaTopics {

    /**
     * Потік evaluation-подій: flag-edge → analytics. Ключ — flagKey.
     */
    public static final String FLAG_EVALUATIONS = "praporets.flag.evaluations.v1";

    /**
     * Отруйні повідомлення після вичерпання ретраїв.
     */
    public static final String FLAG_EVALUATIONS_DLT = "praporets.flag.evaluations.v1.dlt";

    /**
     * Compacted зміни конфігурації: джерело очікуваних ваг
     * rollout. Ключ — environmentKey.
     */
    public static final String FLAG_CHANGES = "praporets.flag.changes.v1";

    private KafkaTopics() {
    }
}
