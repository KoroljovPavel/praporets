package io.praporets.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.uk.Дано;
import io.cucumber.java.uk.Коли;
import io.cucumber.java.uk.Тоді;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кроки {@code propagation.feature}: зміна флага доїжджає до ВСІХ
 * edge-реплік (03e, сценарій 1).
 *
 * <p><b>Стан між кроками:</b> Cucumber створює новий інстанс цього класу на
 * кожен сценарій — додай поля сам (ревізія до перемикання, момент toggle-у
 * для SLA-таймера). Полів у скелеті свідомо немає.
 *
 * <p><b>Контракт часу (ключове!):</b> SLA «1 секунда» міряється від моменту
 * ПОВЕРНЕННЯ {@code toggleFlag(true)} у Коли-кроці до моменту, коли остання
 * репліка звітує увімкнений флаг у Тоді-кроці. Given-кроки — синхронізаційний
 * бар'єр із щедрими дедлайнами (до 60с): якщо попередній сценарій лишив
 * репліки в reconnect-backoff-і, вирівнювання відбувається ТУТ, а не в
 * замірі SLA.
 */
public class PropagationSteps {

    private long toggleNanos;
    private long revisionBeforeToggle;

    /**
     * Піднімає (ліниво) спільний стек {@link E2eStack#ensureStarted()} і
     * перевіряє передумову: {@code replicas} == {@link E2eStack#EDGE_COUNT}
     * (фіча документує топологію — розбіжність має валити сценарій, а не
     * мовчки ігноруватись), {@code environment} == {@link E2eStack#ENVIRONMENT}.
     * Стек уже дочекався readiness усіх реплік — додаткових очікувань не треба.
     */
    @Дано("запущений стек із {int} edge-репліками, підписаними на середовище {string}")
    public void запущений_стек(int replicas, String environment) {
        E2eStack.ensureStarted();
        assertThat(environment).as("Environment повинен збігатися з тестовим").isEqualTo(E2eStack.ENVIRONMENT);
        assertThat(replicas).as("Кількість реплік повинна збігатися з кількістю тестових").isEqualTo(E2eStack.EDGE_COUNT);
    }

    /**
     * Приводить флаг у вимкнений baseline: {@link E2eStack#toggleFlag
     * toggleFlag(false)} і {@link E2eStack#await await} (дедлайн до 60с —
     * це бар'єр, не замір), доки КОЖНА з {@link E2eStack#EDGE_COUNT} реплік
     * не звітує {@code reason=FLAG_DISABLED} (variant {@code off}) через
     * {@link E2eStack#edgeEvaluate edgeEvaluate(i, "UA")}. Побічний ефект —
     * прогрітий шлях пропагації (Kafka-продюсер CP, стріми) перед SLA-заміром.
     */
    @Дано("флаг {string} вимкнено")
    public void флаг_вимкнено(String flagKey) {
        assertThat(flagKey)
            .as("Ключ прапора повинен співпадати з тестовим")
            .isEqualTo(E2eStack.FLAG_KEY);

        E2eStack.toggleFlag(false);

        for (int i = 0; i < E2eStack.EDGE_COUNT; i++) {
            int node = i;
            E2eStack.await("Прапор на репліці %d повинен стати false.".formatted(i), Duration.ofSeconds(60).toMillis(),
                () -> {
                    JsonNode response = E2eStack.edgeEvaluate(node, "UA");
                    return response.get("reason").asText().equals("FLAG_DISABLED") &&
                        response.get("variantKey").asText().equals("off");
                });
        }
    }

    /**
     * Дія оператора: запам'ятай ревізію середовища ДО зміни
     * ({@link E2eStack#currentCpRevision()}) у поле, виконай
     * {@code toggleFlag(true)} і зафіксуй {@code System.nanoTime()} у поле —
     * старт SLA-таймера для наступного кроку.
     */
    @Коли("оператор вмикає флаг {string}")
    public void оператор_вмикає_флаг(String flagKey) {
        assertThat(flagKey)
            .as("Ключ прапора повинен співпадати з тестовим")
            .isEqualTo(E2eStack.FLAG_KEY);

        revisionBeforeToggle = E2eStack.currentCpRevision();

        E2eStack.toggleFlag(true);
        toggleNanos = System.nanoTime();
    }

    /**
     * SLA-перевірка: у межах {@code seconds} від зафіксованого моменту toggle-у
     * КОЖНА з {@code replicas} реплік має звітувати увімкнений флаг —
     * {@code edgeEvaluate(i, "UA")} → {@code reason=RULE_MATCH}, variant
     * {@code on}. Дедлайн await-а рахуй ВІД моменту toggle-у (частина бюджету
     * вже з'їдена HTTP-запитами), не від входу в цей крок.
     *
     * <p>Fail з діагностикою: яка репліка не встигла і що звітувала.
     */
    @Тоді("всі {int} edge-репліки за {int} секунду звітують флаг як увімкнений")
    public void всі_репліки_звітують_увімкнений(int replicas, int seconds) {
        assertThat(replicas).as("Кількість реплік повинна збігатися з кількістю тестових").isEqualTo(E2eStack.EDGE_COUNT);

        for (int i = 0; i < E2eStack.EDGE_COUNT; i++) {
            int node = i;
            long elapsedTime = System.nanoTime() - toggleNanos;
            Duration remainingBudget = Duration.ofSeconds(seconds).minus(Duration.ofNanos(elapsedTime));

            E2eStack.await("Прапор на репліці %d повинен стати true.".formatted(i), remainingBudget.toMillis(),
                () -> {
                    JsonNode response = E2eStack.edgeEvaluate(node, "UA");
                    return response.get("reason").asText().equals("RULE_MATCH") &&
                        response.get("variantKey").asText().equals("on");
                });
        }
    }

    /**
     * Кожна репліка несе НОВУ ревізію: {@code edgeEvaluate(i, "UA").get("revision")}
     * на всіх репліках == {@link E2eStack#currentCpRevision()} (незалежне
     * джерело — БД) і строго більша за ревізію, запам'ятовану ДО toggle-у.
     */
    @Тоді("кожна відповідь несе нову ревізію середовища")
    public void кожна_відповідь_несе_нову_ревізію() {
        for (int i = 0; i < E2eStack.EDGE_COUNT; i++) {
            long replicaRevision = E2eStack.edgeEvaluate(i, "UA").get("revision").asLong();
            assertThat(replicaRevision)
                .as("репліка %d: ревізія має збігатися з БД і бути новішою за %d (до toggle)", i, revisionBeforeToggle)
                .isEqualTo(E2eStack.currentCpRevision())
                .isGreaterThan(revisionBeforeToggle);
        }
    }
}
