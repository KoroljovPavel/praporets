package io.praporets.edge.events;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 03c/E4: контракт генератора UUIDv7. Монотонність усередині однієї
 * мілісекунди НЕ вимагається (дозволено RFC 9562) — порядок перевіряється
 * тільки через межу мілісекунди.
 */
class Uuid7Test {

    @Test
    void version_and_variant_bits_are_correct() {
        UUID id = UUID.fromString(Uuid7.generate());

        assertThat(id.version()).as("версія (біти 48–51 msb)").isEqualTo(7);
        assertThat(id.variant()).as("варіант RFC (старші біти lsb = 10)").isEqualTo(2);
    }

    @Test
    void ids_across_millis_are_lexicographically_ordered() throws InterruptedException {
        String earlier = Uuid7.generate();
        Thread.sleep(5);  // гарантовано інша мілісекунда
        String later = Uuid7.generate();

        // 48-бітний millis-префікс робить канонічний рядок сортованим за часом
        assertThat(earlier.compareTo(later)).isNegative();
    }

    @Test
    void burst_of_ids_is_unique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Uuid7.generate());
        }

        assertThat(ids).hasSize(10_000);
    }
}
