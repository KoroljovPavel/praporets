package io.praporets.core.hash;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Вектори з двох незалежних джерел:
 * 1) опубліковані тест-вектори MurmurHash3 x86_32 (SMHasher/Wikipedia);
 * 2) власні вектори, згенеровані еталонною Python-реалізацією, звіреною з (1).
 * Якщо реалізація проходить ці тести — вона біт-у-біт сумісна з канонічним алгоритмом.
 */
class MurmurHash3Test {

    @ParameterizedTest(name = "murmur3(\"{0}\", seed={1}) == {2}")
    @CsvSource({
            "'',                                            0x00000000, 0x00000000",
            "'',                                            0x00000001, 0x514E28B7",
            "'',                                            0xFFFFFFFF, 0x81F16F39",
            "test,                                          0x00000000, 0xBA6BD213",
            "test,                                          0x9747B28C, 0x704B81DC",
            "'Hello, world!',                               0x00000000, 0xC0363E43",
            "'Hello, world!',                               0x9747B28C, 0x24884CBA",
            "The quick brown fox jumps over the lazy dog,   0x9747B28C, 0x2FA826CD",
    })
    void matches_published_test_vectors(String input, String seedHex, String expectedHex) {
        assertThat(MurmurHash3.hash32(input.getBytes(StandardCharsets.UTF_8), hexToInt(seedHex)))
                .isEqualTo(hexToInt(expectedHex));
    }

    @ParameterizedTest(name = "murmur3(\"{0}\") == {1}")
    @CsvSource({
            "checkout.new-payment-flow:golden-salt-v1:user-0000, 0x487181A4",
            "praporets,                                          0x9712620B",
    })
    void matches_independent_reference_implementation(String input, String expectedHex) {
        assertThat(MurmurHash3.hash32(input, 0)).isEqualTo(hexToInt(expectedHex));
    }

    /** hex → int, бо вектори публікуються unsigned (0x81F16F39 не влазить в Integer.decode). */
    private static int hexToInt(String hex) {
        return (int) Long.decode(hex).longValue();
    }

    @Test
    void string_overload_uses_utf8_not_default_charset() {
        // кирилиця: у UTF-8 це 18 байтів, у будь-якому іншому кодуванні хеш розійдеться
        assertThat(MurmurHash3.hash32("прапорець", 0)).isEqualTo(0xE13BA81C);
    }

    @Test
    void string_overload_is_equivalent_to_byte_overload() {
        String input = "flag.alpha:salt:user-42";

        assertThat(MurmurHash3.hash32(input, 0))
                .isEqualTo(MurmurHash3.hash32(input.getBytes(StandardCharsets.UTF_8), 0));
    }
}
