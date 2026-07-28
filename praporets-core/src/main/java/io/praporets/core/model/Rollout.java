package io.praporets.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Відсотковий розподіл трафіку між варіантами флага.
 *
 * <p>Порядок бакетів <b>значущий</b>: розподіл обчислюється кумулятивним перебором
 * (див. {@link io.praporets.core.evaluation.Bucketer}), тому перестановка бакетів
 * місцями змінює, які користувачі куди потрапляють. Саме стабільний порядок +
 * кумулятивний перебір дають властивість монотонності rollout (ADR-011).
 *
 * <p><b>Інваріанти (перевіряються в компактному конструкторі):</b>
 * <ul>
 *   <li>{@code salt} — не {@code null} і не blank; сіль фіксує розподіл: зміна солі
 *       перетасовує всіх користувачів (використовується для явного «re-shuffle»);</li>
 *   <li>{@code buckets} — не {@code null}, непорожній, без {@code null}-елементів;</li>
 *   <li>сума ваг усіх бакетів дорівнює рівно {@code 100_000};</li>
 *   <li>список бакетів зберігається як незмінна захисна копія: мутація списку,
 *       переданого в конструктор, не впливає на record, а {@link #buckets()}
 *       повертає незмінний список.</li>
 * </ul>
 */
public record Rollout(String salt, List<Bucket> buckets) {

    /** Сума ваг валідного rollout: 100% у стотисячних. */
    public static final int TOTAL_WEIGHT = 100_000;

    /**
     * @param salt сіль хешування — стабілізує розподіл незалежно від назви флага
     * @param buckets впорядковані частки розподілу, сума ваг = {@code 100_000}
     * @throws IllegalArgumentException якщо порушено інваріант значень
     * @throws NullPointerException     якщо {@code buckets} або його елемент {@code null}
     */
    public Rollout {
        Objects.requireNonNull(salt, "salt");
        if (salt.isBlank()) throw new IllegalArgumentException("salt must be non-blank");

        buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets"));
        if (buckets.isEmpty()) throw new IllegalArgumentException("buckets must be non-empty");

        int totalBucketWeight = buckets.stream().mapToInt(Bucket::weight).sum();
        if (totalBucketWeight != TOTAL_WEIGHT)
            throw new IllegalArgumentException("total weight must be " + TOTAL_WEIGHT);
    }
}
