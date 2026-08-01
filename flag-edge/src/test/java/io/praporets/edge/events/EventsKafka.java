package io.praporets.edge.events;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Kafka для тестів публікації подій: той самий образ, що в compose і в
 * control-plane TestKafka. Контейнер — singleton на JVM (start ідемпотентний),
 * живе до кінця прогону, прибирає Ryuk. Крім bootstrap-адреси ресурс ВМИКАЄ
 * events — глобально в {@code %test} вони вимкнені.
 */
public class EventsKafka implements QuarkusTestResourceLifecycleManager {

    public static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    @Override
    public Map<String, String> start() {
        KAFKA.start();
        return Map.of(
            "kafka.bootstrap.servers", KAFKA.getBootstrapServers(),
            "praporets.edge.events.enabled", "true"
        );
    }

    @Override
    public void stop() {
        // свідомо нічого: singleton переживає тест-класи, прибирає Ryuk
    }
}
