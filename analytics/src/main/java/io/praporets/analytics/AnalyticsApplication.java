package io.praporets.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Analytics (спека §4.3): консюмер {@code praporets.flag.evaluations.v1} →
 * похвилинні агрегати в окремій БД {@code praporets_analytics}.
 * Spring Boot 4 + virtual threads; JdbcClient без ORM — свідомо (G1).
 */
@SpringBootApplication
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
