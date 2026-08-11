package io.praporets.edge.config;

import io.praporets.core.model.FlagDefinition;
import io.praporets.core.model.Operator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Специфікує контракт старту edge наскрізь: edge стартує, тягне снапшот із
 * (фейкового) control-plane, стає ready і тримає готову core-конфігурацію
 * в пам'яті.
 *
 * <p>Завантаження фонове, тому тести чекають готовності полінгом, а не
 * сподіваються на порядок подій старту.
 */
@QuarkusTest
@QuarkusTestResource(value = FakeControlPlane.class, restrictToAnnotatedClass = true)
class EdgeStartupTest {

    @Inject
    ConfigStore store;

    private void awaitLoaded() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!store.isLoaded() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(store.isLoaded())
            .as("снапшот мав завантажитись за 10с (фейковий CP відповідає миттєво)")
            .isTrue();
    }

    @Test
    void readiness_is_up_only_after_snapshot_is_loaded() throws Exception {
        awaitLoaded();

        given().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }

    @Test
    void store_holds_snapshot_mapped_to_core_model() throws Exception {
        awaitLoaded();

        ConfigStore.StoredConfig stored = store.current().orElseThrow();

        assertThat(stored.revision()).isEqualTo(FakeControlPlane.SNAPSHOT_REVISION);

        FlagDefinition flag = stored.config().flags().get(FakeControlPlane.FLAG_KEY);
        assertThat(flag).as("флаг зі снапшота має бути в core-конфігурації").isNotNull();
        assertThat(flag.enabled()).isTrue();
        assertThat(flag.defaultVariant()).isEqualTo("on");
        assertThat(flag.variants()).hasSize(2);
        assertThat(flag.rules()).hasSize(1);
        assertThat(flag.rules().getFirst().clauses().getFirst().operator()).isEqualTo(Operator.IN);
        assertThat(flag.rollout()).isNotNull();
        assertThat(flag.rollout().salt()).isEqualTo("v1");

        assertThat(stored.config().segments()).containsKey(FakeControlPlane.SEGMENT_KEY);
    }
}
