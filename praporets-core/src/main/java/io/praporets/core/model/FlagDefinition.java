package io.praporets.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Повна конфігурація одного флага в межах середовища — все, що потрібно для
 * обчислення (дзеркалить {@code FlagDefinition} з proto).
 *
 * <p>Ядро <b>не</b> перевіряє перехресну цілісність ({@code defaultVariant}/
 * {@code offVariant}/варіанти правил існують у {@code variants}) — це робить
 * control-plane при збереженні. Ядро тотальне: посилання на неіснуючий варіант
 * дає {@code jsonValue = null} у результаті, не виключення (D6).
 *
 * <p><b>Інваріанти:</b> {@code key}, {@code defaultVariant}, {@code offVariant}
 * не blank; {@code variants} і {@code rules} — незмінні копії (порожні легальні);
 * {@code rollout} — {@code null}, якщо відсоткового розподілу немає (D7).
 *
 * @param key            глобально унікальний ключ флага (напр. {@code "checkout.new-payment-flow"})
 * @param enabled        false → завжди {@code offVariant}, правила не перевіряються (D5)
 * @param defaultVariant варіант кроку 5 алгоритму (fallback)
 * @param offVariant     варіант при {@code enabled == false}
 * @param variants       можливі значення флага
 * @param rules          впорядковані правила таргетингу; перше збіжне виграє (D1)
 * @param rollout        відсотковий розподіл кроку 4 або {@code null}
 */
public record FlagDefinition(
        String key,
        boolean enabled,
        String defaultVariant,
        String offVariant,
        List<Variant> variants,
        List<Rule> rules,
        Rollout rollout) {

    /**
     * @throws NullPointerException     якщо {@code key}, {@code defaultVariant}, {@code offVariant},
     *                                  {@code variants}, {@code rules} або елемент списків {@code null}
     * @throws IllegalArgumentException якщо {@code key}, {@code defaultVariant} чи {@code offVariant} blank
     */
    public FlagDefinition {
        variants = List.copyOf(variants);
        rules = List.copyOf(rules);
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(defaultVariant, "defaultVariant");
        Objects.requireNonNull(offVariant, "offVariant");
        if (key.isBlank()) throw new IllegalArgumentException("key must be non-blank");
        if (defaultVariant.isBlank()) throw new IllegalArgumentException("defaultVariant must be non-blank");
        if (offVariant.isBlank()) throw new IllegalArgumentException("offVariant must be non-blank");
    }
}
