package io.praporets.edge.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.evaluation.Reason;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 03c/E1+E5: гарячий шлях подій — побудова події, переповнення без
 * блокувань/винятків, лічильник втрат. Дефолтний профіль: publisher
 * вимкнений ({@code %test.praporets.edge.events.enabled=false}), буфер
 * смикається руками — інакше фоновий дренер з'їдав би події між emit-ами
 * і overflow був би недосяжний.
 */
@QuarkusTest
class EvaluationEventsTest {

    @Inject
    EvaluationEvents events;

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "praporets.edge.events.buffer-size")
    int bufferSize;

    /**
     * Буфер спільний на контекст (камінь #5) — кожен тест стартує порожнім.
     */
    @BeforeEach
    void drainLeftovers() {
        events.drainTo(new ArrayList<>(), Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void emit_builds_complete_event() {
        boolean accepted = events.emit(ruleMatch("checkout.new-flow"), 7, "user-42");

        assertThat(accepted).isTrue();
        List<EvaluationEvent> drained = new ArrayList<>();
        assertThat(events.drainTo(drained, 10)).isEqualTo(1);

        EvaluationEvent event = drained.getFirst();
        assertThat(UUID.fromString(event.evaluationId()).version())
            .as("evaluationId — UUIDv7").isEqualTo(7);
        assertThat(event.occurredAt())
            .isAfter(Instant.now().minus(1, ChronoUnit.MINUTES))
            .isBefore(Instant.now().plusSeconds(1));
        assertThat(event.environment()).isEqualTo("dev");
        assertThat(event.flagKey()).isEqualTo("checkout.new-flow");
        assertThat(event.variantKey()).isEqualTo("on");
        assertThat(event.reason()).isEqualTo("RULE_MATCH");
        assertThat(event.ruleId()).isEqualTo("r1");
        assertThat(event.revision()).isEqualTo(7);
        // незалежний оракул: хеш звіряється з власним SHA-256, не з реалізацією
        assertThat(event.userKeyHash()).isEqualTo(sha256Hex("user-42"));
        assertThat(event.edgeInstanceId()).startsWith("flag-edge-");
    }

    @Test
    void flag_not_found_is_an_event_too() {
        // E8: запити невідомих флагів — корисний сигнал для аналітики
        events.emit(new EvaluationResult("no.such.flag", Reason.FLAG_NOT_FOUND, null, null, null), 7, "user-42");

        List<EvaluationEvent> drained = new ArrayList<>();
        assertThat(events.drainTo(drained, 10)).isEqualTo(1);
        assertThat(drained.getFirst().variantKey()).isNull();
        assertThat(drained.getFirst().ruleId()).isNull();
        assertThat(drained.getFirst().reason()).isEqualTo("FLAG_NOT_FOUND");
    }

    @Test
    void overflow_drops_newest_and_counts_them_without_blocking() {
        double droppedBefore = droppedCount();
        EvaluationResult result = ruleMatch("flag.overflow");

        // ревізія = порядковий номер — маркер, ХТО саме вижив у буфері
        for (int i = 0; i < bufferSize + 5; i++) {
            events.emit(result, i, "user-42");
        }

        List<EvaluationEvent> drained = new ArrayList<>();
        int total = events.drainTo(drained, Integer.MAX_VALUE);

        assertThat(total).as("буфер тримає рівно свою ємність").isEqualTo(bufferSize);
        // drop-newest: викидаються НОВІ події, найстаріші доїжджають (E1)
        assertThat(drained.getFirst().revision()).isEqualTo(0);
        assertThat(drained.getLast().revision()).isEqualTo(bufferSize - 1);
        assertThat(droppedCount() - droppedBefore)
            .as("лічильник %s{reason=buffer_full}", EvaluationEvents.DROPPED_METRIC)
            .isEqualTo(5.0);
    }

    @Test
    void drain_respects_max_and_leaves_rest() {
        EvaluationResult result = ruleMatch("flag.drain");
        for (int i = 0; i < 5; i++) {
            events.emit(result, i, "user-42");
        }

        List<EvaluationEvent> first = new ArrayList<>();
        assertThat(events.drainTo(first, 3)).isEqualTo(3);
        assertThat(first).hasSize(3);

        List<EvaluationEvent> rest = new ArrayList<>();
        assertThat(events.drainTo(rest, 100)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private static EvaluationResult ruleMatch(String flagKey) {
        return new EvaluationResult(flagKey, Reason.RULE_MATCH, "on", "true", "r1");
    }

    private double droppedCount() {
        Counter counter = registry.find(EvaluationEvents.DROPPED_METRIC)
            .tag("reason", "buffer_full")
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
