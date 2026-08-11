package io.praporets.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Analytics-сервіс: консюмер {@code praporets.flag.evaluations.v1} →
 * похвилинні агрегати в окремій БД {@code praporets_analytics}.
 * Spring Boot 4 + virtual threads; доступ до БД через JdbcClient/JdbcTemplate
 * без ORM — свідомо: вся логіка модуля і є кількома SQL-ами.
 */
@SpringBootApplication
@EnableScheduling
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
