package io.praporets.edge.evaluation;

import io.praporets.edge.config.ConfigStore;
import io.praporets.edge.config.FakeControlPlane;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * 02e: REST-обчислення (E-03, V5) + контракт помилок RFC 9457 (V6).
 * Store не мутується — дефолтний профіль, спільний контекст із
 * {@code EdgeStartupTest}; канонічний снапшот ревізії 7.
 */
@QuarkusTest
@QuarkusTestResource(value = FakeControlPlane.class, restrictToAnnotatedClass = true)
class EvaluationRestTest {

    @Inject
    ConfigStore store;

    @BeforeEach
    void awaitLoaded() throws InterruptedException {
        await("снапшот завантажено", () -> store.isLoaded());
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    private static String body(String environment, String flagKey) {
        return """
            {
              "environmentKey": "%s",
              "flagKey": "%s",
              "context": {"userKey": "user-42", "attributes": {"country": "UA"}}
            }
            """.formatted(environment, flagKey);
    }

    @Test
    void evaluate_returns_full_result() {
        given()
            .contentType(ContentType.JSON)
            .body(body(FakeControlPlane.ENVIRONMENT, FakeControlPlane.FLAG_KEY))
            .when()
            .post("/api/v1/evaluate")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("flagKey", equalTo(FakeControlPlane.FLAG_KEY))
            .body("variantKey", equalTo("on"))
            .body("jsonValue", equalTo("true"))
            .body("reason", equalTo("RULE_MATCH"))
            .body("ruleId", equalTo("r1"))
            .body("revision", equalTo((int) FakeControlPlane.SNAPSHOT_REVISION));
    }

    @Test
    void unknown_flag_is_200_with_flag_not_found() {
        given()
            .contentType(ContentType.JSON)
            .body(body(FakeControlPlane.ENVIRONMENT, "no.such.flag"))
            .when()
            .post("/api/v1/evaluate")
            .then()
            .statusCode(200)
            .body("reason", equalTo("FLAG_NOT_FOUND"))
            .body("variantKey", nullValue())
            .body("revision", equalTo((int) FakeControlPlane.SNAPSHOT_REVISION));
    }

    @Test
    void blank_flag_key_is_400_problem_json() {
        given()
            .contentType(ContentType.JSON)
            .body(body(FakeControlPlane.ENVIRONMENT, ""))
            .when()
            .post("/api/v1/evaluate")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("status", equalTo(400));
    }

    @Test
    void foreign_environment_is_404_problem_json() {
        given()
            .contentType(ContentType.JSON)
            .body(body("prod", FakeControlPlane.FLAG_KEY))
            .when()
            .post("/api/v1/evaluate")
            .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("status", equalTo(404))
            .body("detail", equalTo("Environment [prod] is not served by this edge"));
    }
}
