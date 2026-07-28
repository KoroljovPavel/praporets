package io.praporets.core.evaluation;

import java.util.Objects;

/**
 * Результат обчислення одного флага для одного контексту.
 *
 * <p><b>Матриця узгодженості полів із {@code reason}</b> (перевіряється в
 * компактному конструкторі — неможливий стан має бути непредставним):
 *
 * <pre>
 * reason          | variantKey | jsonValue      | ruleId
 * ----------------+------------+----------------+------------------
 * FLAG_NOT_FOUND  | null       | null           | null
 * FLAG_DISABLED   | обов'язк.  | null якщо D6   | null
 * RULE_MATCH      | обов'язк.  | null якщо D6   | обов'язковий
 * ROLLOUT         | обов'язк.  | null якщо D6   | опційний: заданий, якщо
 *                 |            |                | rollout прийшов із правила
 * DEFAULT         | обов'язк.  | null якщо D6   | null
 * </pre>
 *
 * «null якщо D6» — {@code jsonValue} може бути {@code null}, лише коли
 * {@code variantKey} не знайдено серед {@code variants} флага (розсинхрон
 * конфігурації, який ядро переживає без виключення).
 *
 * @param flagKey    ключ обчисленого флага (завжди заданий)
 * @param reason     чому обрано цей варіант
 * @param variantKey ключ обраного варіанта; {@code null} лише для FLAG_NOT_FOUND
 * @param jsonValue  значення варіанта як JSON-рядок; {@code null} для FLAG_NOT_FOUND та D6
 * @param ruleId     id правила, що спрацювало; див. матрицю
 */
public record EvaluationResult(
        String flagKey,
        Reason reason,
        String variantKey,
        String jsonValue,
        String ruleId) {

    /**
     * @throws NullPointerException     якщо {@code flagKey} або {@code reason} {@code null}
     * @throws IllegalArgumentException якщо {@code flagKey} blank або комбінація полів
     *                                  суперечить матриці вище
     */
    public EvaluationResult {
        Objects.requireNonNull(flagKey, "flagKey");
        Objects.requireNonNull(reason, "reason");
        if (flagKey.isBlank()) throw new IllegalArgumentException("flagKey must be non-blank");

        switch (reason) {
            case FLAG_NOT_FOUND         -> require(variantKey == null && jsonValue == null && ruleId == null, "FLAG_NOT_FOUND: variantKey, jsonValue, ruleId must be all null");
            case RULE_MATCH             -> require(variantKey != null && ruleId != null, "RULE_MATCH: variantKey, ruleId must be all non-null");
            case ROLLOUT                -> require(variantKey != null, "ROLLOUT: variantKey must be non-null");
            case FLAG_DISABLED, DEFAULT -> require(variantKey != null && ruleId == null , "FLAG_DISABLED or DEFAULT: variantKey must be non-null and ruleId must be null");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
