package io.praporets.analytics.stats;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/stats} (A-05). Тонкий шар: параметри → сервіс.
 *
 * <p><b>Залежності для інжекту:</b> {@link StatsService}.
 *
 * <p><b>Метод (твоя робота):</b> {@code @GetMapping} з чотирма
 * ОБОВ'ЯЗКОВИМИ {@code @RequestParam}: {@code environment}, {@code flag},
 * {@code from}, {@code to}. Часові — типом {@code Instant}: Boot сам
 * конвертує ISO-8601 (камінь #4), руками нічого не парсити. Відсутній
 * параметр → 400 {@code ProblemDetail} безкоштовно
 * ({@code spring.mvc.problemdetails.enabled} уже в yaml).
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {
}
