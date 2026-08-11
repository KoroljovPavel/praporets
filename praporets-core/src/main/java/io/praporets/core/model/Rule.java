package io.praporets.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Правило таргетингу: «якщо користувач збігається з усіма умовами — віддати
 * конкретний варіант АБО розподілити відсотково».
 *
 * <p>Приклад: {@code IF user IN segment "beta-testers" THEN variant "treatment-a"},
 * або {@code IF country IN [UA] THEN rollout 50/50}.
 *
 * <p><b>Рівно один результат:</b> правило має або {@code variantKey}
 * (фіксований варіант), або {@code rollout} (розподіл усередині правила) —
 * не обидва і не жодного. Друге поле — {@code null}.
 *
 * <p><b>Інші інваріанти:</b> {@code id} не blank ({@code id} потрапляє в
 * {@code EvaluationResult.ruleId} — по ньому оператор бачить, яке правило
 * спрацювало); {@code clauses} — незмінна копія; порожній список легальний і
 * означає «матчить усіх».
 *
 * @param id         стабільний ідентифікатор правила
 * @param clauses    умови, AND між ними; порожній список матчить усіх
 * @param variantKey фіксований варіант-результат або {@code null}
 * @param rollout    відсотковий розподіл або {@code null}
 */
public record Rule(String id, List<Clause> clauses, String variantKey, Rollout rollout) {

    /**
     * @throws NullPointerException     якщо {@code id}, {@code clauses} або елемент {@code null}
     * @throws IllegalArgumentException якщо {@code id} blank, або порушено D3
     *                                  (обидва {@code variantKey}/{@code rollout} задані чи обидва {@code null})
     */
    public Rule {
        clauses = List.copyOf(clauses);
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        if (variantKey != null && rollout != null)
            throw new IllegalArgumentException("variantKey and rollout cannot be both non-null");
        else if (variantKey == null && rollout == null)
            throw new IllegalArgumentException("variantKey and rollout cannot be both null");
    }
}
