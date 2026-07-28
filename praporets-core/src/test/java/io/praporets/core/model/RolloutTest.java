package io.praporets.core.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolloutTest {

    @Test
    void rejects_blank_salt() {
        assertThatThrownBy(() -> new Rollout("  ", List.of(new Bucket("on", 100_000))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_empty_bucket_list() {
        assertThatThrownBy(() -> new Rollout("salt", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_weights_not_summing_to_total() {
        assertThatThrownBy(() -> new Rollout("salt", List.of(
                new Bucket("treatment", 10_000),
                new Bucket("control", 80_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100000");
    }

    @Test
    void rejects_negative_bucket_weight() {
        assertThatThrownBy(() -> new Bucket("treatment", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_weight_above_total() {
        assertThatThrownBy(() -> new Bucket("treatment", 100_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_blank_variant_key() {
        assertThatThrownBy(() -> new Bucket(" ", 100_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allows_zero_weight_bucket() {
        // нульова вага легальна: тримає позицію варіанта для майбутнього збільшення
        var rollout = new Rollout("salt", List.of(
                new Bucket("treatment", 0),
                new Bucket("control", 100_000)));

        assertThat(rollout.buckets()).hasSize(2);
    }

    @Test
    void bucket_list_is_defensively_copied() {
        var source = new ArrayList<>(List.of(new Bucket("on", 100_000)));
        var rollout = new Rollout("salt", source);

        source.add(new Bucket("off", 0));

        assertThat(rollout.buckets()).hasSize(1);
    }

    @Test
    void bucket_list_is_immutable() {
        var rollout = new Rollout("salt", List.of(new Bucket("on", 100_000)));

        assertThatThrownBy(() -> rollout.buckets().add(new Bucket("off", 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
