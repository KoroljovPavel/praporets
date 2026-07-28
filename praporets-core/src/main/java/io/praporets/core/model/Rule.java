package io.praporets.core.model;

import java.util.List;

/**
 * Правило таргетингу: «якщо користувач збігається з усіма умовами — віддати
 * конкретний варіант АБО розподілити відсотково».
 *
 * <p>Приклад: {@code IF user IN segment "beta-testers" THEN variant "treatment-a"},
 * або {@code IF country IN [UA] THEN rollout 50/50}.
 *
 * <p><b>Рівно один результат (D3):</b> правило має або {@code variantKey}
 * (фіксований варіант), або {@code rollout} (розподіл усередині правила) —
 * не обидва і не жодного. Друге поле — {@code null}.
 *
 * <p><b>Інші інваріанти:</b> {@code id} не blank ({@code id} потрапляє в
 * {@code EvaluationResult.ruleId} — по ньому оператор бачить, яке правило
 * спрацювало); {@code clauses} — незмінна копія; порожній список легальний і
 * означає «матчить усіх» (D2, консистентно з S6).
 *
 * @param id         стабільний ідентифікатор правила
 * @param clauses    умови, AND між ними (S1); порожній список матчить усіх (D2)
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
        throw new UnsupportedOperationException("01d: implement me");
    }
}
