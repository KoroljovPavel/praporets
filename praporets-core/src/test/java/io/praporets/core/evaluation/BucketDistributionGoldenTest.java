package io.praporets.core.evaluation;

import io.praporets.core.model.Bucket;
import io.praporets.core.model.Rollout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-тест: 1000 фіксованих пар (userKey → variant), згенерованих незалежною
 * еталонною реалізацією алгоритму (Python, звірений із канонічними векторами MurmurHash3).
 *
 * <p>Призначення: захист від <b>випадкової</b> зміни алгоритму хешування чи бакетування
 * при рефакторингу. Падіння цього тесту означає, що всі користувачі всіх rollout
 * мовчки перетасувалися б у проді. Файл {@code golden-buckets.csv} НЕ перегенеровується
 * «під реалізацію» — якщо тест упав, зламана реалізація, а не файл.
 */
class BucketDistributionGoldenTest {

    private static final Rollout GOLDEN_ROLLOUT = new Rollout("golden-salt-v1", List.of(
            new Bucket("control", 50_000),
            new Bucket("treatment-a", 30_000),
            new Bucket("treatment-b", 20_000)));

    @Test
    void every_golden_user_resolves_to_recorded_variant() {
        List<String> lines = readGoldenFile();
        assertThat(lines).hasSize(1_000);

        for (String line : lines) {
            String[] parts = line.split(",");
            String userKey = parts[0];
            String expectedVariant = parts[1];

            assertThat(Bucketer.variantKeyFor(GOLDEN_ROLLOUT, "checkout.new-payment-flow", userKey))
                    .as("user %s", userKey)
                    .isEqualTo(expectedVariant);
        }
    }

    @Test
    void golden_distribution_is_close_to_configured_weights() {
        // самодокументація файлу: 50/30/20 на 1000 користувачів → 515/300/185
        List<String> lines = readGoldenFile();

        long control = lines.stream().filter(l -> l.endsWith(",control")).count();
        long treatmentA = lines.stream().filter(l -> l.endsWith(",treatment-a")).count();
        long treatmentB = lines.stream().filter(l -> l.endsWith(",treatment-b")).count();

        assertThat(control).isEqualTo(515);
        assertThat(treatmentA).isEqualTo(300);
        assertThat(treatmentB).isEqualTo(185);
    }

    private static List<String> readGoldenFile() {
        try (var in = BucketDistributionGoldenTest.class.getResourceAsStream("/golden-buckets.csv")) {
            assertThat(in).as("resource /golden-buckets.csv").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
