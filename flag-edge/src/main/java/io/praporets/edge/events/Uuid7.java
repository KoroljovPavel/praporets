package io.praporets.edge.events;

/**
 * Генератор UUIDv7 (E4): time-ordered id для evaluation-подій. У JDK версії 7
 * немає, а A-04 (ідемпотентність) і аналітика хочуть id, що сортується за
 * часом створення.
 *
 * <p><b>Layout (RFC 9562):</b>
 * <pre>
 * msb: [48 біт unix-epoch-millis][4 біти версії = 0b0111][12 біт random]
 * lsb: [2 біти варіанта = 0b10][62 біти random]
 * </pre>
 *
 * <p><b>Реалізація (твоя робота):</b> {@code System.currentTimeMillis()} +
 * {@code ThreadLocalRandom}; зібрати два long бітовими операціями і віддати
 * {@code new java.util.UUID(msb, lsb).toString()}. Монотонність УСЕРЕДИНІ
 * однієї мілісекунди не гарантуємо (це дозволено RFC і достатньо для
 * аналітики) — тест перевіряє порядок лише через межу мілісекунди.
 */
public final class Uuid7 {

    private Uuid7() {
    }

    /**
     * Новий UUIDv7 у канонічному рядковому вигляді.
     */
    public static String generate() {
        throw new UnsupportedOperationException("03c: твоя реалізація");
    }
}
