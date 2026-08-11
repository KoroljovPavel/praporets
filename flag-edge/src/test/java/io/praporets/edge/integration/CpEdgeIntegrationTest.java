package io.praporets.edge.integration;

import io.praporets.edge.config.ConfigStore;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.function.BooleanSupplier;

import static io.praporets.edge.integration.RealControlPlane.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Специфікує наскрізний контракт CP↔edge проти СПРАВЖНЬОГО control-plane
 * (Testcontainers: Postgres + Kafka + CP з bootJar). Синтаксис контракту
 * перевіряє компілятор через спільні proto-файли, а семантику
 * (SnapshotRequired при завеликому розриві, реконект, catch-up) — саме цей
 * тест: фейка тут немає, снапшот, стрім, дельти, heartbeat-и і
 * SnapshotRequired — усе від реального сервера з реальної БД.
 *
 * <p>Сценарії впорядковані і йдуть однією історією (стан БД накопичується).
 * Хелпери — в {@link RealControlPlane} (toggle, stop/start, bump,
 * currentCpRevision) і локальні нижче (await, edge-evaluate через REST 8081).
 */
@QuarkusTest
@QuarkusTestResource(value = RealControlPlane.class, restrictToAnnotatedClass = true)
@TestProfile(CpEdgeIntegrationTest.IntegrationProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CpEdgeIntegrationTest {

    public static class IntegrationProfile implements QuarkusTestProfile {
    }

    @Inject
    ConfigStore store;

    /**
     * Чекає умову до {@code timeoutMillis}; впала — валить тест із поясненням.
     */
    private static void await(String what, long timeoutMillis, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    private long edgeRevision() {
        return store.current().map(ConfigStore.StoredConfig::revision).orElse(-1L);
    }

    private static String body(String environment, String flagKey, String country) {
        return """
            {
              "environmentKey": "%s",
              "flagKey": "%s",
              "context": {"userKey": "user-42", "attributes": {"country": "%s"}}
            }
            """.formatted(environment, flagKey, country);
    }

    /**
     * Edge стартував проти реального CP (ресурс відпрацював до тестів):
     * <ol>
     *   <li>store завантажений, ревізія edge == {@link RealControlPlane#currentCpRevision()}
     *       (незалежне джерело — БД, не відповідь CP);</li>
     *   <li>REST-обчислення на edge (спільний порт 8081) для context
     *       {@code country=UA} → {@code RULE_MATCH}, variant {@code on}
     *       (правило посіяне сідингом);</li>
     *   <li>для {@code country=DE} → {@code DEFAULT} (rollout відсутній).</li>
     * </ol>
     */
    @Test
    @Order(1)
    void edge_bootstraps_from_real_control_plane() throws Exception {
        await("Wait for store loaded", 10_000, () -> store.isLoaded());
        assertThat(edgeRevision()).isEqualTo(currentCpRevision());
        restEvaluate("UA")
            .body("flagKey", equalTo(RealControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("on"))
            .body("jsonValue", equalTo("true"))
            .body("reason", equalTo("RULE_MATCH"))
            .body("ruleId", equalTo("r1"))
            .body("revision", equalTo((int) currentCpRevision()));

        restEvaluate("DE")
            .body("flagKey", equalTo(RealControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("on"))
            .body("jsonValue", equalTo("true"))
            .body("reason", equalTo("DEFAULT"))
            .body("ruleId", nullValue())
            .body("revision", equalTo((int) currentCpRevision()));
    }

    /**
     * Пропагація зміни end-to-end: {@link RealControlPlane#toggleFlag
     * toggleFlag(false)} через CP REST → дельта доїжджає до edge (дедлайн
     * await 3с, фактично — сотні мс); evaluate → {@code FLAG_DISABLED},
     * variant {@code off}, ревізія у відповіді == нова ревізія БД.
     * Наприкінці флаг повертається назад ({@code toggleFlag(true)} + await) —
     * наступні сценарії стартують із увімкненого стану.
     */
    @Test
    @Order(2)
    void flag_change_propagates_end_to_end() throws Exception {
        await("Wait for store loaded", 10_000, () -> store.isLoaded());
        toggleFlag(false);
        await("Await revision", 3_000, () -> edgeRevision() == currentCpRevision());

        restEvaluate("UA")
            .body("flagKey", equalTo(RealControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("off"))
            .body("jsonValue", equalTo("false"))
            .body("reason", equalTo("FLAG_DISABLED"))
            .body("ruleId", nullValue())
            .body("revision", equalTo((int) currentCpRevision()));

        toggleFlag(true);
        await("Await revision", 3_000, () -> edgeRevision() == currentCpRevision());
    }

    /**
     * Виживання edge при падінні CP:
     * <ol>
     *   <li>{@link RealControlPlane#stopControlPlane()};</li>
     *   <li>edge ДАЛІ відповідає: evaluate → 200, той самий результат,
     *       та сама ревізія (вибір AP — store не чіпається під час розриву);</li>
     *   <li>{@link RealControlPlane#startControlPlane()} (ті самі порти);</li>
     *   <li>toggle після воскресіння + await до 60с (backoff-кап 30с!) —
     *       зміна доїжджає: реконект+catch-up працюють проти реального CP.</li>
     * </ol>
     */
    @Test
    @Order(3)
    void edge_survives_cp_outage_and_recovers() throws Exception {
        await("Wait for store loaded", 10_000, () -> store.isLoaded());
        stopControlPlane();
        restEvaluate("UA")
            .body("flagKey", equalTo(RealControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("on"))
            .body("jsonValue", equalTo("true"))
            .body("reason", equalTo("RULE_MATCH"))
            .body("ruleId", equalTo("r1"))
            .body("revision", equalTo((int) edgeRevision()));
        startControlPlane();
        long beforeToggle = edgeRevision();
        toggleFlag(false);
        await("Flag toggled to false, wait for new revision", 60_000, () -> edgeRevision() > beforeToggle);

        restEvaluate("UA")
            .body("flagKey", equalTo(RealControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("off"))
            .body("jsonValue", equalTo("false"))
            .body("reason", equalTo("FLAG_DISABLED"))
            .body("ruleId", nullValue())
            .body("revision", equalTo((int) currentCpRevision()));

        toggleFlag(true);
        await("Flag changed to true, wait for revision is up", 60_000, () -> edgeRevision() > beforeToggle + 1);
    }

    /**
     * Семантика SnapshotRequired — головне, що не перевіриш компіляцією
     * proto-контракту:
     * <ol>
     *   <li>{@link RealControlPlane#stopControlPlane()};</li>
     *   <li>{@link RealControlPlane#bumpRevisionInDatabase bumpRevisionInDatabase(1000)}
     *       — стрибок повз revision-window (500); безпечно лише при
     *       лежачому CP;</li>
     *   <li>{@link RealControlPlane#startControlPlane()};</li>
     *   <li>await до 60с: edge-ревізія == {@link RealControlPlane#currentCpRevision()}
     *       (стара + 1000) — тобто CP відповів SnapshotRequired на завеликий
     *       розрив, а edge перезавантажив ПОВНИЙ снапшот і відкрив новий стрім;</li>
     *   <li>контрольний evaluate: конфігурація жива (variant той самий) —
     *       перезавантаження не загубило дані.</li>
     * </ol>
     */
    @Test
    @Order(4)
    void revision_gap_beyond_window_forces_full_snapshot_reload() throws Exception {
        await("Wait for store loaded", 10_000, () -> store.isLoaded());
        stopControlPlane();
        bumpRevisionInDatabase(1000);
        startControlPlane();
        await("Wait for new revision", 60_000, () -> edgeRevision() == currentCpRevision());

        restEvaluate("UA")
            .body("flagKey", equalTo(RealControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("on"))
            .body("jsonValue", equalTo("true"))
            .body("reason", equalTo("RULE_MATCH"))
            .body("ruleId", equalTo("r1"))
            .body("revision", equalTo((int) currentCpRevision()));
    }

    private ValidatableResponse restEvaluate(String country) {
        return given()
            .contentType(ContentType.JSON)
            .body(body(RealControlPlane.ENVIRONMENT, RealControlPlane.FLAG_KEY, country))
            .when()
            .post("/api/v1/evaluate")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }
}
