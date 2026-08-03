package io.praporets.e2e;

import io.cucumber.java.PendingException;
import io.cucumber.java.uk.Дано;
import io.cucumber.java.uk.Коли;
import io.cucumber.java.uk.Тоді;

/**
 * Кроки {@code cp-outage.feature}: edge переживає падіння control-plane і
 * сам наздоганяє після воскресіння (03e, сценарій 2; e2e-рідня 02f Order(3)).
 *
 * <p><b>Стан між кроками:</b> додай поля сам (ревізія на момент падіння,
 * baseline метрики застарілості). «Edge-репліка» сценарію — репліка 0
 * ({@code E2eStack.edgeRestBase(0)}); решта реплік живуть поруч і теж
 * переживають падіння — сценарій свідомо дивиться на одну.
 *
 * <p><b>Інваріант сценарію наприкінці:</b> CP знову живий і репліки наздогнали
 * актуальну ревізію — наступні сценарії стартують зі здорового стека.
 * Останній Тоді-крок зобов'язаний це гарантувати.
 */
public class CpOutageSteps {

    /**
     * {@link E2eStack#ensureStarted()}; запам'ятай у поля поточну ревізію
     * репліки 0 ({@code edgeEvaluate(0, "UA").get("revision")}) і baseline
     * {@link E2eStack#edgeMetric edgeMetric(0, STALENESS_METRIC)}. Стек уже
     * дочекався readiness — конфігурація гарантовано завантажена.
     */
    @Дано("edge-репліка із завантаженою конфігурацією")
    public void edge_репліка_із_завантаженою_конфігурацією() {
        // TODO: реалізуй крок (ensureStarted + зафіксуй ревізію і baseline staleness)
        throw new PendingException();
    }

    /**
     * {@link E2eStack#stopControlPlane()}. Postgres/Kafka/edge-и живі —
     * падає рівно control-plane (модель: деплой/аварія CP у проді).
     */
    @Коли("control-plane стає недоступним")
    public void control_plane_стає_недоступним() {
        // TODO: реалізуй крок
        throw new PendingException();
    }

    /**
     * Вибір AP наочно: {@code edgeEvaluate(0, "UA")} повертає 200 із ТИМ
     * САМИМ результатом і ТІЄЮ САМОЮ ревізією, що зафіксована до падіння
     * ({@code reason=RULE_MATCH}, variant {@code on}) — store під час розриву
     * не чіпається.
     */
    @Тоді("edge-репліка далі успішно обчислює флаги")
    public void edge_репліка_далі_обчислює() {
        // TODO: реалізуй крок (evaluate → 200, результат і ревізія як до падіння)
        throw new PendingException();
    }

    /**
     * {@code await} (дедлайн ~10с), доки {@code edgeMetric(0, STALENESS_METRIC)}
     * не стане строго більшим за baseline з Дано-кроку — вік конфігурації
     * росте, бо стрім мертвий і жоден sync його не скидає.
     *
     * <p>Нюанс чесності: staleness росте і при живому CP (між heartbeat-ами,
     * до 15с) — тому асертимо «більше за baseline», а не «більше за нуль».
     * Строгіша перевірка «перевищив heartbeat-інтервал» коштувала б 15с сну —
     * свідомо не робимо.
     */
    @Тоді("метрика застарілості конфігурації зростає")
    public void метрика_застарілості_зростає() {
        // TODO: реалізуй крок (await: staleness > baseline)
        throw new PendingException();
    }

    /**
     * {@link E2eStack#startControlPlane()} — той самий host-порт REST і той
     * самий network-alias {@code control-plane}; реєстр стрімів CP після
     * рестарту порожній, репліки перепідключаться самі за backoff-ом.
     */
    @Коли("control-plane оживає")
    public void control_plane_оживає() {
        // TODO: реалізуй крок
        throw new PendingException();
    }

    /**
     * Доказ catch-up-у через НОВУ зміну (сам факт реконекту ззовні не видно —
     * ревізія без змін не росте): {@code toggleFlag(false)} → await (дедлайн
     * 60с: кап backoff-у 30с + DNS-кеш JVM до 30с), доки
     * {@code edgeEvaluate(0, "UA").get("revision")} не дорівняється
     * {@link E2eStack#currentCpRevision()} і reason не стане
     * {@code FLAG_DISABLED}. Потім поверни {@code toggleFlag(true)} + такий
     * самий await ПО ВСІХ репліках — інваріант здорового стека для наступних
     * сценаріїв (див. JavaDoc класу).
     */
    @Тоді("edge-репліка перепідключається і наздоганяє актуальну ревізію")
    public void edge_репліка_наздоганяє() {
        // TODO: реалізуй крок (toggle → await ревізії; відкат toggle → await по всіх репліках)
        throw new PendingException();
    }
}
