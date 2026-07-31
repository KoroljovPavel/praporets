package io.praporets.edge.evaluation;

import io.praporets.core.evaluation.EvaluationResult;
import io.praporets.core.model.EvaluationContext;
import io.praporets.grpc.evaluation.v1.EvaluateResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Мапінг між proto-контрактом {@code praporets.evaluation.v1} і core —
 * дзеркало {@link io.praporets.edge.config.ProtoToCoreMapper}, але в
 * протилежний бік: core-результат → proto-відповідь (V4).
 *
 * <p>Без залежностей — чистий stateless-мапер.
 */
@ApplicationScoped
public class ResultProtoMapper {

    /**
     * Proto-контекст запиту → core-контекст.
     *
     * <p>Пряме перенесення {@code userKey} і {@code attributes}. Валідність
     * (blank userKey) тут НЕ перевіряється — це робота викликача на межі
     * (камінь #2): core-конструктор кидає IAE, і летіти воно має як
     * {@code INVALID_ARGUMENT}/400, а не як помилка мапінгу.
     *
     * @param protoContext контекст із запиту (не {@code null})
     * @return core-контекст із тими самими даними
     */
    public io.praporets.core.model.EvaluationContext toCoreContext(
        io.praporets.grpc.evaluation.v1.EvaluationContext protoContext) {

        String userKey = protoContext.getUserKey();
        Map<String, String> attributes = protoContext.getAttributesMap();

        return new EvaluationContext(userKey, attributes);
    }

    /**
     * Core-результат → proto-відповідь.
     *
     * <p><b>Контракт:</b>
     * <ul>
     *   <li>{@code flagKey}, {@code variantKey}, {@code jsonValue},
     *       {@code ruleId} переносяться як є; core-{@code null} → поле не
     *       сетається (proto3 віддасть порожній рядок; {@code set*(null)}
     *       кидає NPE — камінь #3);</li>
     *   <li>{@code reason} — через {@link #toProtoReason};</li>
     *   <li>{@code revision} — з переданого знімка store (V2: те саме
     *       значення, на якому обчислювали).</li>
     * </ul>
     *
     * @param result   результат core {@code Evaluator} (не {@code null})
     * @param revision ревізія знімка конфігурації, на якому обчислено
     * @return готова proto-відповідь
     */
    public EvaluateResponse toResponse(EvaluationResult result, long revision) {

        EvaluateResponse.Builder response = EvaluateResponse.newBuilder()
            .setFlagKey(result.flagKey())
            .setReason(toProtoReason(result.reason()))
            .setRevision(revision);

        if (result.variantKey() != null)
            response.setVariantKey(result.variantKey());

        if (result.jsonValue() != null)
            response.setJsonValue(result.jsonValue());

        if (result.ruleId() != null)
            response.setRuleId(result.ruleId());

        return response.build();
    }

    /**
     * Core-{@code Reason} → proto-{@code Reason} <b>явним switch-ом</b> (V4).
     *
     * <p>Імена значень збігаються, але {@code valueOf} за ім'ям заборонений
     * свідомо: proto-enum має значення, яких немає в core
     * ({@code REASON_UNSPECIFIED}, {@code ERROR}), і зворотна розбіжність
     * (нове core-значення без proto-пари) має падати компіляцією
     * (exhaustive switch), а не рантаймом.
     *
     * @param reason core-причина (не {@code null})
     * @return відповідне proto-значення
     */
    public io.praporets.grpc.evaluation.v1.Reason toProtoReason(
        io.praporets.core.evaluation.Reason reason) {
        return switch (reason) {
            case ROLLOUT -> io.praporets.grpc.evaluation.v1.Reason.ROLLOUT;
            case DEFAULT -> io.praporets.grpc.evaluation.v1.Reason.DEFAULT;
            case RULE_MATCH -> io.praporets.grpc.evaluation.v1.Reason.RULE_MATCH;
            case FLAG_DISABLED -> io.praporets.grpc.evaluation.v1.Reason.FLAG_DISABLED;
            case FLAG_NOT_FOUND -> io.praporets.grpc.evaluation.v1.Reason.FLAG_NOT_FOUND;
        };
    }
}
