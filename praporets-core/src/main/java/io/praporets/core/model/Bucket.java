package io.praporets.core.model;

import java.util.Objects;

/**
 * Одна частка відсоткового розподілу (rollout): скільки трафіку отримує варіант.
 *
 * <p>Ваги виражені у <b>стотисячних</b>: {@code 100_000} = 100%, {@code 10_000} = 10%.
 * Це дає точність 0.001% без чисел з рухомою комою.
 *
 * <p><b>Інваріанти (перевіряються в компактному конструкторі):</b>
 * <ul>
 *   <li>{@code variantKey} — не {@code null} і не blank;</li>
 *   <li>{@code weight} — в діапазоні {@code [0, 100_000]}. Нульова вага дозволена:
 *       вона тримає варіант у списку зі стабільною позицією, що важливо для
 *       монотонності при подальшому збільшенні ваги.</li>
 * </ul>
 */
public record Bucket(String variantKey, int weight) {

    /**
     * @param variantKey ключ варіанта, на який вказує ця частка (напр. {@code "treatment-a"})
     * @param weight     вага у стотисячних, {@code [0, 100_000]}
     * @throws IllegalArgumentException якщо будь-який інваріант порушено
     * @throws NullPointerException якщо {@code variantKey} null
     */
    public Bucket {
        Objects.requireNonNull(variantKey, "variantKey");
        if (variantKey.isBlank())
            throw new IllegalArgumentException("variantKey must be non-blank");

        if (weight < 0 || weight > Rollout.TOTAL_WEIGHT)
            throw new IllegalArgumentException("weight must be in [0, " + Rollout.TOTAL_WEIGHT + "]");
    }
}
