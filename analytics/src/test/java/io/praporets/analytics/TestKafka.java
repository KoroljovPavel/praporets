package io.praporets.analytics;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton-Kafka (KRaft) — той самий образ, що в compose і тестах CP/edge.
 * Auto-create топіків у контейнері лишається дефолтним (true) — тести
 * перевіряють обробку подій, не топологію (її декларує control-plane).
 */
public final class TestKafka {

    public static final KafkaContainer INSTANCE =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    static {
        INSTANCE.start();
    }

    private TestKafka() {
    }
}
