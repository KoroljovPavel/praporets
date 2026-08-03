package io.praporets.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.uk.Дано;
import io.cucumber.java.uk.Коли;
import io.cucumber.java.uk.Тоді;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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

    private long revisionBeforeDown;
    private double baselineBeforeDown;

    /**
     * {@link E2eStack#ensureStarted()}; далі — синхронізаційний бар'єр
     * (камінь №4 спеки): {@code await} (60с), доки ревізія репліки 0
     * ({@code edgeEvaluate(0, "UA").get("revision")}) не дорівняється
     * {@link E2eStack#currentCpRevision()} — попередній сценарій
     * (concurrent-edits) робить PUT config і НЕ чекає доїзду дельти до
     * edge; без бар'єра ревізія, знята тут, гонить із дельтою в польоті
     * і Тоді-асерт «та сама ревізія» флакає. ПІСЛЯ бар'єра запам'ятай у
     * поля ревізію і baseline {@link E2eStack#edgeMetric edgeMetric(0,
     * STALENESS_METRIC)}.
     */
    @Дано("edge-репліка із завантаженою конфігурацією")
    public void edge_репліка_із_завантаженою_конфігурацією() {
        E2eStack.ensureStarted();

        E2eStack.await("Репліка 0 має наздогнати ревізю БД перед падінням CP", Duration.ofSeconds(60).toMillis(),
            () -> E2eStack.edgeEvaluate(0, "UA").get("revision").asLong() == E2eStack.currentCpRevision());

        revisionBeforeDown = E2eStack.edgeEvaluate(0, "UA").get("revision").asLong();
        baselineBeforeDown = E2eStack.edgeMetric(0, E2eStack.STALENESS_METRIC);
    }

    /**
     * {@link E2eStack#stopControlPlane()}. Postgres/Kafka/edge-и живі —
     * падає рівно control-plane (модель: деплой/аварія CP у проді).
     */
    @Коли("control-plane стає недоступним")
    public void control_plane_стає_недоступним() {
        E2eStack.stopControlPlane();
    }

    /**
     * Вибір AP наочно: {@code edgeEvaluate(0, "UA")} повертає 200 із ТИМ
     * САМИМ результатом і ТІЄЮ САМОЮ ревізією, що зафіксована до падіння
     * ({@code reason=RULE_MATCH}, variant {@code on}) — store під час розриву
     * не чіпається.
     */
    @Тоді("edge-репліка далі успішно обчислює флаги")
    public void edge_репліка_далі_обчислює() {
        JsonNode body = E2eStack.edgeEvaluate(0, "UA");

        assertThat(body.get("revision").asLong())
            .as("Edge повинен відповідати тією ж ревізією що і перед падінням")
            .isEqualTo(revisionBeforeDown);
        assertThat(body.get("reason").asText())
            .as("Edge повинен відповідати тією самою причиною що і перед падінням")
            .isEqualTo("RULE_MATCH");
        assertThat(body.get("variantKey").asText())
            .as("Edge повинен відповідати ти самим варіантом що і перед падінням")
            .isEqualTo("on");
    }

    /**
     * {@code await} (дедлайн 20с — понад heartbeat-інтервал 15с: heartbeat
     * між зняттям baseline і падінням міг скинути staleness у нуль, і рости
     * до baseline метриці доведеться заново), доки {@code edgeMetric(0,
     * STALENESS_METRIC)} не стане строго більшим за baseline з Дано-кроку —
     * вік конфігурації росте, бо стрім мертвий і жоден sync його не скидає.
     *
     * <p>Нюанс чесності: staleness росте і при живому CP (між heartbeat-ами,
     * до 15с) — тому асертимо «більше за baseline», а не «більше за нуль».
     * Строгіша перевірка «перевищив heartbeat-інтервал» коштувала б 15с сну —
     * свідомо не робимо.
     */
    @Тоді("метрика застарілості конфігурації зростає")
    public void метрика_застарілості_зростає() {
        E2eStack.await("Метріка більша з збережену перед падінням", Duration.ofSeconds(20).toMillis(),
            () -> E2eStack.edgeMetric(0, E2eStack.STALENESS_METRIC) > baselineBeforeDown);
    }

    /**
     * {@link E2eStack#startControlPlane()} — той самий host-порт REST і той
     * самий network-alias {@code control-plane}; реєстр стрімів CP після
     * рестарту порожній, репліки перепідключаться самі за backoff-ом.
     */
    @Коли("control-plane оживає")
    public void control_plane_оживає() {
        E2eStack.startControlPlane();
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
        E2eStack.toggleFlag(false);

        E2eStack.await("Зміни прапора повинні примінитись після підняття", Duration.ofSeconds(60).toMillis(),
            () -> {
                JsonNode result = E2eStack.edgeEvaluate(0, "UA");
                return result.get("revision").asLong() == E2eStack.currentCpRevision() &&
                    result.get("reason").asText().equals("FLAG_DISABLED");
            });

        E2eStack.toggleFlag(true);

        for (int i = 0; i < E2eStack.EDGE_COUNT; i++) {
            int node = i;
            E2eStack.await("Прапор на репліці %d повинен бути true.".formatted(i), Duration.ofSeconds(60).toMillis(),
                () -> {
                    JsonNode response = E2eStack.edgeEvaluate(node, "UA");
                    return response.get("reason").asText().equals("RULE_MATCH") &&
                        response.get("variantKey").asText().equals("on");
                });
        }
    }
}
