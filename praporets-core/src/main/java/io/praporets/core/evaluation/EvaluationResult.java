package io.praporets.core.evaluation;

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
        throw new UnsupportedOperationException("01d: implement me");
    }
}
