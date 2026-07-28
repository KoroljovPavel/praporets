package io.praporets.core.evaluation;

import io.praporets.core.model.Clause;
import io.praporets.core.model.EvaluationContext;
import io.praporets.core.model.Operator;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S4 як property: обчислення тотальне. Який би сміттєвий вміст не прийшов у
 * значення атрибута чи values клоза — matches() повертає boolean, не кидає.
 * Це критично для edge: одна крива конфігурація не має класти обчислення флагів.
 */
class ClauseEvaluatorPropertyTest {

    @Property
    void evaluation_never_throws_for_any_operator_and_any_data(
            @ForAll Operator operator,
            @ForAll @StringLength(max = 50) String attributeValue,
            @ForAll @StringLength(max = 50) String clauseValue,
            @ForAll boolean negate) {

        var clause = new Clause("attr", operator, List.of(clauseValue), negate);
        var context = new EvaluationContext("user-1", Map.of("attr", attributeValue));

        assertThatCode(() -> ClauseEvaluator.matches(clause, context, Map.of()))
                .doesNotThrowAnyException();
    }
}
