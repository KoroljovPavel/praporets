package io.praporets.core.evaluation;

import io.praporets.core.model.Bucket;
import io.praporets.core.model.Rollout;
import java.util.List;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Властивості розподілу зі спеки, розділ 7.2. Числові межі в тестах рівномірності
 * та незалежності звірені з еталонною Python-реалізацією: для цих конкретних вхідних
 * даних коректна реалізація дає treatment=10058/100000 і 172/1000 розбіжностей.
 */
class BucketerTest {

    private static Rollout tenPercentTreatment(String salt) {
        return new Rollout(salt, List.of(
                new Bucket("treatment", 10_000),
                new Bucket("control", 90_000)));
    }

    @Test
    void same_input_always_yields_same_variant() {
        var rollout = tenPercentTreatment("determinism-salt");
        String first = Bucketer.variantKeyFor(rollout, "determinism.flag", "user-42");

        for (int i = 0; i < 10_000; i++) {
            assertThat(Bucketer.variantKeyFor(rollout, "determinism.flag", "user-42"))
                    .isEqualTo(first);
        }
    }

    @Test
    void distribution_matches_weights_within_half_percentage_point() {
        var rollout = tenPercentTreatment("uniformity-salt");

        long treatment = 0;
        for (int i = 0; i < 100_000; i++) {
            if (Bucketer.variantKeyFor(rollout, "uniformity.flag", "user-" + i).equals("treatment")) {
                treatment++;
            }
        }

        // 10% ± 0.5 п.п. на 100k користувачів (NFR зі спеки; фактичне значення 10058)
        assertThat(treatment).isBetween(9_500L, 10_500L);
    }

    @Test
    void increasing_rollout_never_moves_users_out_of_treatment() {
        var at10 = tenPercentTreatment("mono-salt");
        var at30 = new Rollout("mono-salt", List.of(
                new Bucket("treatment", 30_000),
                new Bucket("control", 70_000)));

        for (int i = 0; i < 10_000; i++) {
            String user = "user-" + i;
            if (Bucketer.variantKeyFor(at10, "mono.flag", user).equals("treatment")) {
                assertThat(Bucketer.variantKeyFor(at30, "mono.flag", user))
                        .as("user %s was in treatment at 10%% and must stay at 30%%", user)
                        .isEqualTo("treatment");
            }
        }
    }

    @Property
    void increasing_first_bucket_weight_never_evicts_its_users(
            @ForAll @AlphaChars @StringLength(min = 1, max = 32) String userKey,
            @ForAll @IntRange(min = 1, max = 99_998) int smallerWeight,
            @ForAll @IntRange(min = 2, max = 99_999) int largerWeight) {
        Assume.that(smallerWeight < largerWeight);

        var before = new Rollout("property-salt", List.of(
                new Bucket("treatment", smallerWeight),
                new Bucket("control", Bucketer.TOTAL_WEIGHT - smallerWeight)));
        var after = new Rollout("property-salt", List.of(
                new Bucket("treatment", largerWeight),
                new Bucket("control", Bucketer.TOTAL_WEIGHT - largerWeight)));

        if (Bucketer.variantKeyFor(before, "property.flag", userKey).equals("treatment")) {
            assertThat(Bucketer.variantKeyFor(after, "property.flag", userKey))
                    .isEqualTo("treatment");
        }
    }

    @Test
    void same_user_lands_in_different_buckets_on_different_flags() {
        var rollout = tenPercentTreatment("shared-salt");

        int differ = 0;
        for (int i = 0; i < 1_000; i++) {
            String user = "user-" + i;
            if (!Bucketer.variantKeyFor(rollout, "flag.alpha", user)
                    .equals(Bucketer.variantKeyFor(rollout, "flag.beta", user))) {
                differ++;
            }
        }

        // якби flagKey не входив у хеш, differ був би 0; фактичне значення 172
        assertThat(differ).isGreaterThan(50);
    }

    @Test
    void changing_salt_reshuffles_users() {
        var saltA = tenPercentTreatment("salt-a");
        var saltB = tenPercentTreatment("salt-b");

        int differ = 0;
        for (int i = 0; i < 1_000; i++) {
            String user = "user-" + i;
            if (!Bucketer.variantKeyFor(saltA, "reshuffle.flag", user)
                    .equals(Bucketer.variantKeyFor(saltB, "reshuffle.flag", user))) {
                differ++;
            }
        }

        assertThat(differ).isGreaterThan(50);
    }
}
