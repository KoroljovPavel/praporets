package io.praporets.core.evaluation;

import io.praporets.core.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Головний алгоритм обчислення флага. Чиста тотальна функція:
 * жодного стану, I/O чи виключень через вміст конфігурації — саме її
 * використовують і edge (гарячий шлях), і control-plane (dry-run preview),
 * тому вона живе в спільному ядрі.
 *
 * <p><b>Порядок кроків (контракт):</b>
 * <ol>
 *   <li>флага немає → {@code FLAG_NOT_FOUND} (без варіанта, клієнт підставить свій default);</li>
 *   <li>{@code !enabled} → {@code offVariant} / {@code FLAG_DISABLED} — правила не перевіряються;</li>
 *   <li>правила по порядку, перше, чиї clauses всі збіглися
 *       ({@code ClauseEvaluator.matchesAll} із сегментами config):
 *       з rollout → бакет / {@code ROLLOUT} + ruleId; інакше → варіант / {@code RULE_MATCH} + ruleId;</li>
 *   <li>{@code flag.rollout != null} → бакет / {@code ROLLOUT} без ruleId;</li>
 *   <li>{@code defaultVariant} / {@code DEFAULT}.</li>
 * </ol>
 *
 * <p>Бакетування — {@link Bucketer#variantKeyFor}: у хеш іде <b>flagKey</b>
 * (не rule.id) — розподіли правила й конфігурації флага з однаковим salt
 * лягають на ту саму сітку точок.
 *
 * <p>{@code jsonValue} результату — значення першого варіанта з відповідним
 * ключем; якщо такого варіанта немає — {@code null}.
 */
public final class Evaluator {

    private Evaluator() {
    }

    /**
     * Обчислює один флаг.
     *
     * @param config  конфігурація середовища (не {@code null})
     * @param flagKey ключ флага (не {@code null}; відсутність флага — це
     *                {@code FLAG_NOT_FOUND}-результат, не помилка)
     * @param context контекст користувача (не {@code null})
     * @return результат; ніколи {@code null}, ніколи не кидає через вміст конфігурації
     * @throws NullPointerException якщо будь-який аргумент {@code null}
     */
    public static EvaluationResult evaluate(EnvironmentConfig config, String flagKey, EvaluationContext context) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(flagKey, "flagKey");
        Objects.requireNonNull(context, "context");

        FlagDefinition flag = config.flags().get(flagKey);

        if (flag == null) return new EvaluationResult(flagKey, Reason.FLAG_NOT_FOUND, null, null, null);
        if (!flag.enabled()) {
            return new EvaluationResult(flagKey, Reason.FLAG_DISABLED, flag.offVariant(), extractJsonValue(flag, flag.offVariant()), null);
        }

        for (Rule rule : flag.rules()) {
            if (ClauseEvaluator.matchesAll(rule.clauses(), context, config.segments())) {
                if (rule.rollout() != null) {
                    String variantKey = Bucketer.variantKeyFor(rule.rollout(), flagKey, context.userKey());
                    return new EvaluationResult(flagKey, Reason.ROLLOUT, variantKey, extractJsonValue(flag, variantKey), rule.id());
                } else {
                    return new EvaluationResult(flagKey, Reason.RULE_MATCH, rule.variantKey(), extractJsonValue(flag, rule.variantKey()), rule.id());
                }
            }
        }

        if (flag.rollout() != null) {
            String variantKey = Bucketer.variantKeyFor(flag.rollout(), flagKey, context.userKey());
            return new EvaluationResult(flagKey, Reason.ROLLOUT, variantKey, extractJsonValue(flag, variantKey), null);
        }

        return new EvaluationResult(flagKey, Reason.DEFAULT, flag.defaultVariant(), extractJsonValue(flag, flag.defaultVariant()), null);
    }

    private static String extractJsonValue(FlagDefinition flag, String variantKey) {
        return flag.variants().stream()
            .filter(v -> v.key().equals(variantKey))
            .map(Variant::jsonValue)
            .findFirst()
            .orElse(null);
    }

    /**
     * Обчислює всі флаги середовища для одного контексту (типовий старт сесії SDK).
     *
     * @return по одному результату на кожен флаг, відсортовано за flagKey (D8 —
     *         детермінований порядок відповіді); порожній список для порожньої конфігурації
     * @throws NullPointerException якщо будь-який аргумент {@code null}
     */
    public static List<EvaluationResult> evaluateAll(EnvironmentConfig config, EvaluationContext context) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(context, "context");
        return config.flags().values().stream()
            .map(flag -> evaluate(config, flag.key(), context))
            .sorted(Comparator.comparing(EvaluationResult::flagKey))
            .toList();
    }
}
