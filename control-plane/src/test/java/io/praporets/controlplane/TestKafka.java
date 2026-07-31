package io.praporets.controlplane;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Єдиний Kafka-контейнер на всю JVM тестового прогону — та сама логіка й
 * причини, що в {@link TestPostgres}: singleton переживає всі тест-класи,
 * Spring-контексти з кешу не лишаються з мертвим bootstrap-адресом;
 * прибирає Ryuk.
 *
 * <p>KRaft, той самий образ, що в compose — тести й dev-стенд на одній
 * версії брокера.
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
