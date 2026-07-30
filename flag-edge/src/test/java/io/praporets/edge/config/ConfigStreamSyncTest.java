package io.praporets.edge.config;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 02d: наскрізний контракт стріму дельт (E-02/06/07). Сценарії керують
 * фейковим CP вручну, тому тести впорядковані (@Order) і йдуть як одна
 * розповідь: ревізія монотонно росте від тесту до тесту.
 *
 * <p>Окремий {@link TestProfile} — свіжий Quarkus-контекст: сценарії
 * змінюють ревізію store, і без ізоляції зламали б інваріанти
 * {@code EdgeStartupTest} з 02c (той перевіряє рівно ревізію снапшота).
 *
 * <p>read-timeout навмисно великий (60s): реконекти в цих тестах мають
 * траплятись ТІЛЬКИ коли їх викликає сценарій, а не фоновий таймаут тиші.
 */
@QuarkusTest
@QuarkusTestResource(FakeControlPlane.class)
@TestProfile(ConfigStreamSyncTest.StreamProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigStreamSyncTest {

    public static class StreamProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("praporets.edge.stream.read-timeout", "60s");
        }
    }

    @Inject
    ConfigStore store;

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    private long currentRevision() {
        return store.current().map(ConfigStore.StoredConfig::revision).orElse(-1L);
    }

    private boolean flagEnabled() {
        return store.current().orElseThrow().config()
            .flags().get(FakeControlPlane.FLAG_KEY).enabled();
    }

    @Test
    @Order(1)
    void stream_opens_from_snapshot_revision_after_startup() throws Exception {
        await("снапшот завантажено", () -> store.isLoaded());
        FakeControlPlane.awaitSessionCount(1, 10_000);

        assertThat(FakeControlPlane.latestSession().fromRevision())
            .as("стрім відкривається з ревізії щойно завантаженого снапшота")
            .isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
        assertThat(currentRevision()).isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);
        assertThat(flagEnabled()).isTrue();
    }

    @Test
    @Order(2)
    void live_delta_is_applied_atomically_to_store() throws Exception {
        FakeControlPlane.latestSession().sendDelta(8, false);

        await("дельта ревізії 8 застосована", () -> currentRevision() == 8);
        assertThat(flagEnabled()).isFalse();
    }

    @Test
    @Order(3)
    void duplicate_or_stale_delta_is_ignored() throws Exception {
        // та сама ревізія 8, але з enabled=true: дубль catch-up/live (D4) —
        // якби застосувався, флаг «відкотився б» у true
        FakeControlPlane.latestSession().sendDelta(8, true);

        Thread.sleep(400);
        assertThat(currentRevision()).isEqualTo(8);
        assertThat(flagEnabled()).as("дубль ревізії не переписує стан").isFalse();
    }

    @Test
    @Order(4)
    void broken_stream_reconnects_with_current_revision_and_store_survives() throws Exception {
        int sessionsBefore = FakeControlPlane.sessionCount();

        FakeControlPlane.latestSession().abort();

        FakeControlPlane.awaitSessionCount(sessionsBefore + 1, 10_000);
        assertThat(FakeControlPlane.latestSession().fromRevision())
            .as("reconnect несе ОСТАННЮ ревізію, не ревізію снапшота")
            .isEqualTo(8);
        // весь цей час edge продовжував тримати конфігурацію (вибір AP)
        assertThat(store.isLoaded()).isTrue();
        assertThat(currentRevision()).isEqualTo(8);
    }

    @Test
    @Order(5)
    void heartbeat_revision_gap_triggers_reconnect_without_snapshot() throws Exception {
        int sessionsBefore = FakeControlPlane.sessionCount();
        int snapshotsBefore = FakeControlPlane.snapshotCalls();

        // CP каже: жива ревізія 9, а edge на 8 → дельту втрачено десь дорогою
        FakeControlPlane.latestSession().sendHeartbeat(9);

        FakeControlPlane.awaitSessionCount(sessionsBefore + 1, 10_000);
        assertThat(FakeControlPlane.latestSession().fromRevision()).isEqualTo(8);
        assertThat(FakeControlPlane.snapshotCalls())
            .as("heartbeat-розрив лікується реконектом, НЕ снапшотом")
            .isEqualTo(snapshotsBefore);

        // сервер (як справжній CP у 02b) докидає склеєну дельту — edge доганяє
        FakeControlPlane.latestSession().sendDelta(9, true);
        await("catch-up дельта 9 застосована", () -> currentRevision() == 9);
        assertThat(flagEnabled()).isTrue();
    }

    @Test
    @Order(6)
    void snapshot_required_reloads_full_snapshot() throws Exception {
        int snapshotsBefore = FakeControlPlane.snapshotCalls();
        FakeControlPlane.setSnapshotRevision(11);

        FakeControlPlane.latestSession().sendSnapshotRequired();

        await("перезавантажено зі снапшота ревізії 11", () -> currentRevision() == 11);
        assertThat(FakeControlPlane.snapshotCalls()).isGreaterThan(snapshotsBefore);
        // новий стрім відкрито вже з ревізії свіжого снапшота (камінь #5)
        assertThat(FakeControlPlane.latestSession().fromRevision()).isEqualTo(11);
    }

    @Test
    @Order(7)
    void metrics_expose_staleness_and_revision() {
        String metrics = given().get("/q/metrics").then().statusCode(200).extract().asString();

        assertThat(metrics).contains("flag_edge_config_staleness_seconds");
        assertThat(metrics).contains("flag_edge_config_revision");
        assertThat(metrics)
            .as("gauge ревізії відбиває поточний стан store")
            .containsPattern("flag_edge_config_revision\\S*\\s+11\\.0");
    }
}
