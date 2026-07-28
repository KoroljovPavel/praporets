package io.praporets.core.hash;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3, варіант x86_32 — стабільний некриптографічний хеш.
 *
 * <p>Навіщо свій: {@code praporets-core} не має залежностей (ZERO frameworks),
 * тож Guava/commons-codec недоступні. Алгоритм — публічний референс Остіна Епплбі
 * ({@code MurmurHash3.cpp}, функція {@code MurmurHash3_x86_32}).
 *
 * <p><b>Контракт сумісності:</b> результат має біт-у-біт збігатися з референсною
 * реалізацією (і будь-якою іншою коректною: python-mmh3, Guava, Kafka). Це перевіряється
 * тестом на опублікованих векторах. Зміна результату хешу = мовчазне перетасування
 * всіх користувачів у всіх rollout — тому будь-який рефакторинг тут захищений тестами.
 *
 * <p>Деталі реалізації, де найлегше помилитися:
 * <ul>
 *   <li>4-байтові блоки читаються <b>little-endian</b>;</li>
 *   <li>циклічні зсуви — {@link Integer#rotateLeft(int, int)};</li>
 *   <li>хвіст (останні 1–3 байти) мікшується окремо, без оновлення h1 rotl-кроком;</li>
 *   <li>перед фінальним avalanche: {@code h1 ^= length}.</li>
 * </ul>
 */
public final class MurmurHash3 {

    private MurmurHash3() {
    }

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    /**
     * Хеш масиву байтів.
     *
     * @param data байти (не {@code null}, може бути порожнім)
     * @param seed початкове значення; {@code hash32(new byte[0], 0) == 0}
     * @return 32-бітний хеш (знаковий int — інтерпретація unsigned на совісті викликача)
     */
    public static int hash32(byte[] data, int seed) {
        if (data == null) {
            throw new NullPointerException("data must not be null");
        }

        int length = data.length;

        int h1 = seed;
        int nblocks = length / 4;

        for (int i = 0; i < nblocks; i++) {
            int index = i * 4;
            int k1 = (data[index] & 0xff)
                | ((data[index + 1] & 0xff) << 8)
                | ((data[index + 2] & 0xff) << 16)
                | (data[index + 3] << 24);

            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tailIndex = nblocks * 4;

        switch (length & 3) {
            case 3:
                k1 ^= (data[tailIndex + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[tailIndex + 1] & 0xff) << 8;
            case 1:
                k1 ^= (data[tailIndex] & 0xff);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= C2;
                h1 ^= k1;
        }

        h1 ^= length;

        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

    /**
     * Зручність: хеш рядка, закодованого в <b>UTF-8</b> (явно, не дефолтний charset —
     * інакше результат залежить від налаштувань JVM і ламає крос-платформність).
     *
     * @param text рядок (не {@code null})
     * @param seed початкове значення
     * @return {@code hash32(text.getBytes(UTF_8), seed)}
     */
    public static int hash32(String text, int seed) {
        return hash32(text.getBytes(StandardCharsets.UTF_8), seed);
    }
}
