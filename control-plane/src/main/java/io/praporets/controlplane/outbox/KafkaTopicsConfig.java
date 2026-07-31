package io.praporets.controlplane.outbox;

import io.praporets.controlplane.common.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Декларація топіків (K1): {@code KafkaAdmin} із spring-kafka знаходить усі
 * {@code NewTopic}-біни і створює відсутні при старті (ідемпотентно; існуючі
 * не чіпає). Auto-create на брокері вимкнений свідомо — топологія топіка
 * має жити в коді, а не виникати як side effect першої відправки.
 *
 * <p><b>Бін (твоя реалізація):</b> {@code KafkaTopics.FLAG_CHANGES},
 * 3 партиції, replication factor 1 (один брокер у dev/тестах; у K8s це
 * стане values-параметром), config {@code cleanup.policy=compact} —
 * зручно через {@code TopicBuilder.name(...).partitions(...).compact()}.
 */
@Configuration
public class KafkaTopicsConfig {
    @Bean
    NewTopic flagChangesTopic() {
        return TopicBuilder.name(KafkaTopics.FLAG_CHANGES)
            .partitions(3)
            .replicas(1)
            .compact()
            .build();
    }
}
