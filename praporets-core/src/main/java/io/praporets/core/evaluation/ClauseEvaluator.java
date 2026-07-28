package io.praporets.core.evaluation;

import io.praporets.core.model.Clause;
import io.praporets.core.model.EvaluationContext;
import io.praporets.core.model.Segment;
import java.util.List;
import java.util.Map;

/**
 * Обчислення умов таргетингу над контекстом користувача. Єдине місце зі switch
 * по sealed-ієрархії {@link ClauseMatcher} — <b>без {@code default}</b>, повноту
 * гарантує компілятор.
 *
 * <p><b>Семантика (рішення S1–S7 зі спеки кроку 01c):</b>
 * <ul>
 *   <li>S1: між clauses — AND ({@link #matchesAll}), між values одного clause — OR;</li>
 *   <li>S2: відсутній атрибут → базовий результат false; {@code negate} інвертує
 *       будь-який базовий результат (negate + відсутній атрибут = true);</li>
 *   <li>S3: атрибут {@code "userKey"} резолвиться з {@link EvaluationContext#userKey()};</li>
 *   <li>S4: обчислення тотальне — ніколи не кидає через вміст даних (невалідне
 *       значення атрибута → false); NPE лише за null-аргументи;</li>
 *   <li>S5: невідомий сегмент → false; IN_SEGMENT всередині сегмента → false
 *       (сегменти не вкладаються);</li>
 *   <li>S6: порожні values → false; сегмент із порожніми clauses матчить усіх;</li>
 *   <li>S7: рядкові порівняння чутливі до регістру.</li>
 * </ul>
 */
public final class ClauseEvaluator {

    private ClauseEvaluator() {
    }

    /**
     * Чи збігається одна умова з контекстом.
     *
     * @param clause   умова (не {@code null})
     * @param context  контекст користувача (не {@code null})
     * @param segments сегменти середовища за ключем, для {@code IN_SEGMENT} (не {@code null})
     * @return результат з урахуванням {@code clause.negate()}
     * @throws NullPointerException якщо будь-який аргумент {@code null}
     */
    public static boolean matches(Clause clause, EvaluationContext context, Map<String, Segment> segments) {
        throw new UnsupportedOperationException("01c: implement me");
    }

    /**
     * Чи збігаються <b>усі</b> умови (AND, S1). Порожній список → {@code true}
     * (нема умов — нема заперечень; саме так сегмент без clauses матчить усіх, S6).
     *
     * @throws NullPointerException якщо будь-який аргумент {@code null}
     */
    public static boolean matchesAll(List<Clause> clauses, EvaluationContext context, Map<String, Segment> segments) {
        throw new UnsupportedOperationException("01c: implement me");
    }
}
