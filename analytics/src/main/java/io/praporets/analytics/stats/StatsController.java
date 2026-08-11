package io.praporets.analytics.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * {@code GET /api/v1/stats} — тонкий шар: параметри → {@link StatsService}.
 * Усі чотири параметри обов'язкові; часові приймаються як {@code Instant}
 * (ISO-8601 конвертує Boot). Відсутній параметр → 400 з RFC 9457
 * {@code ProblemDetail} ({@code spring.mvc.problemdetails.enabled}).
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsResponse getStats(@RequestParam String environment,
                                  @RequestParam String flag,
                                  @RequestParam Instant from,
                                  @RequestParam Instant to) {
        return statsService.stats(environment, flag, from, to);
    }
}
