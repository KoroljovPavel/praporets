package io.praporets.core.evaluation;

import java.util.Optional;

/**
 * Мінімальна семантична версія: {@code major.minor.patch}, лише числа.
 *
 * <p>Правило S8: приймаємо 1–3 числові частини через крапку ({@code "5"}, {@code "5.2"},
 * {@code "5.2.1"}), відсутні частини = 0. Все інше — невалідно: суфікси
 * ({@code "5.2.1-beta"}), префікс {@code "v5"}, нечислові чи порожні частини
 * ({@code "5.x"}, {@code "5..1"}), більше трьох частин. Це свідоме спрощення
 * повного SemVer 2.0 — нам потрібно порівнювати версії застосунків, не range-и npm.
 *
 * <p>Порівняння — числове покомпонентне: {@code 5.10.0 > 5.9.9} (лексикографічне
 * порівняння рядків дало б навпаки — саме тому цей клас існує).
 *
 * <p><b>Інваріанти:</b> всі компоненти невід'ємні.
 *
 * @param major основна версія
 * @param minor друга компонента, 0 якщо відсутня
 * @param patch третя компонента, 0 якщо відсутня
 */
public record Semver(int major, int minor, int patch) implements Comparable<Semver> {

    /**
     * @throws IllegalArgumentException якщо будь-яка компонента від'ємна
     */
    public Semver {
        throw new UnsupportedOperationException("01c: implement me");
    }

    /**
     * Парсить рядок за правилом S8.
     *
     * @param raw рядок версії (не {@code null})
     * @return версія або {@link Optional#empty()} для невалідного формату
     * @throws NullPointerException якщо {@code raw} {@code null}
     */
    public static Optional<Semver> parse(String raw) {
        throw new UnsupportedOperationException("01c: implement me");
    }

    /**
     * Числове покомпонентне порівняння: major, потім minor, потім patch.
     */
    @Override
    public int compareTo(Semver other) {
        throw new UnsupportedOperationException("01c: implement me");
    }
}
