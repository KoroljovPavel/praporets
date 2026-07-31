package io.praporets.edge.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

/**
 * 02f (I2): СПРАВЖНІЙ control-plane для інтеграційного тесту — Testcontainers
 * замість фейка. Піднімає ізольовану мережу: Postgres 17 + CP (bootJar на
 * eclipse-temurin:25-jre; шлях до jar передає Gradle через system property
 * {@code praporets.cp.jar}). Edge (тестовий Quarkus) дивиться на CP через
 * host-порти.
 *
 * <p><b>Host-порти CP фіксовані</b> (обираються вільні на старті й пінятся
 * через {@code setPortBindings}): рестарт контейнера в сценаріях
 * outage/recovery зберігає ті самі порти — інакше edge після «воскресіння» CP
 * шукав би його на старому mapped-порту вічно.
 *
 * <p>Сідинг при старті (через CP REST, як зробив би оператор): environment
 * {@code dev}, флаг {@code checkout.new-flow} (BOOLEAN, variants on=true /
 * off=false), config: enabled, правило {@code r1: country IN [UA] → on},
 * без rollout.
 */
public class RealControlPlane implements QuarkusTestResourceLifecycleManager {

    public static final String ENVIRONMENT = "dev";
    public static final String FLAG_KEY = "checkout.new-flow";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static Network network;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> controlPlane;
    private static int cpRestPort;
    private static int cpGrpcPort;

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withDatabaseName("praporets")
            .withUsername("praporets")
            .withPassword("praporets");
        postgres.start();

        cpRestPort = freePort();
        cpGrpcPort = freePort();
        controlPlane = buildControlPlaneContainer();
        controlPlane.start();

        seed();

        return Map.of(
            "quarkus.grpc.clients.config.host", "localhost",
            "quarkus.grpc.clients.config.port", String.valueOf(cpGrpcPort),
            "quarkus.grpc.clients.config.test-port", String.valueOf(cpGrpcPort),
            "quarkus.grpc.clients.config.plain-text", "true",
            "praporets.edge.environment", ENVIRONMENT
        );
    }

    private static GenericContainer<?> buildControlPlaneContainer() {
        String jarPath = System.getProperty("praporets.cp.jar");
        if (jarPath == null) {
            throw new IllegalStateException(
                "system property praporets.cp.jar не заданий — тест запущено повз Gradle? "
                    + "(:flag-edge:test прокидає шлях до bootJar)");
        }
        GenericContainer<?> cp = new GenericContainer<>("eclipse-temurin:25-jre")
            .withNetwork(network)
            .withCopyFileToContainer(MountableFile.forHostPath(jarPath), "/app/cp.jar")
            .withCommand("java", "-jar", "/app/cp.jar")
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/praporets")
            .withEnv("SPRING_DATASOURCE_USERNAME", "praporets")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "praporets")
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8080)
                .withStartupTimeout(Duration.ofMinutes(2)));
        // фіксовані host-порти: переживають stop()/start() контейнера (I2)
        cp.setPortBindings(java.util.List.of(cpRestPort + ":8080", cpGrpcPort + ":9090"));
        return cp;
    }

    @Override
    public void stop() {
        if (controlPlane != null) controlPlane.stop();
        if (postgres != null) postgres.stop();
        if (network != null) network.close();
    }

    // ------------------------------------------------ керування з тестів

    /**
     * База CP REST, напр. {@code http://localhost:54321}.
     */
    public static String restBase() {
        return "http://localhost:" + cpRestPort;
    }

    /**
     * Зупиняє CP (Postgres живий — стан переживає падіння).
     */
    public static void stopControlPlane() {
        controlPlane.stop();
    }

    /**
     * Піднімає CP заново на тих самих host-портах.
     */
    public static void startControlPlane() {
        controlPlane = buildControlPlaneContainer();
        controlPlane.start();
    }

    /**
     * Поточна ревізія середовища прямо з БД (незалежне джерело правди для assert-ів).
     */
    public static long currentCpRevision() {
        try (Connection c = jdbc(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT revision FROM environment WHERE key = '" + ENVIRONMENT + "'")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Не зміг прочитати ревізію з БД", e);
        }
    }

    /**
     * I4: стрибок ревізії повз revision-window (500) прямо в БД. Викликати
     * ТІЛЬКИ при зупиненому CP (камінь #6 спеки).
     */
    public static void bumpRevisionInDatabase(long delta) {
        try (Connection c = jdbc(); Statement s = c.createStatement()) {
            int updated = s.executeUpdate(
                "UPDATE environment SET revision = revision + " + delta + " WHERE key = '" + ENVIRONMENT + "'");
            if (updated != 1) throw new IllegalStateException("environment '" + ENVIRONMENT + "' не знайдено");
        } catch (SQLException e) {
            throw new IllegalStateException("Не зміг стрибнути ревізію в БД", e);
        }
    }

    /**
     * Перемикає enabled нашого флага через CP REST (той самий шлях, що в оператора).
     */
    public static void toggleFlag(boolean enabled) {
        HttpResponse<String> response = post(
            "/api/v1/environments/" + ENVIRONMENT + "/flags/" + FLAG_KEY + "/toggle",
            "{\"enabled\": " + enabled + "}");
        if (response.statusCode() != 200) {
            throw new IllegalStateException("toggle → " + response.statusCode() + ": " + response.body());
        }
    }

    // ------------------------------------------------ нутрощі

    private static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void seed() {
        expect(201, post("/api/v1/environments",
            "{\"key\": \"" + ENVIRONMENT + "\", \"name\": \"Dev\"}"));
        expect(201, post("/api/v1/flags", """
            {"key": "%s", "name": "New checkout flow", "valueType": "BOOLEAN",
             "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]}
            """.formatted(FLAG_KEY)));
        // перший upsert конфігурації = створення → 201 (200 було б при оновленні)
        expect(201, put("/api/v1/environments/" + ENVIRONMENT + "/flags/" + FLAG_KEY + "/config", """
            {"enabled": true, "defaultVariant": "on", "offVariant": "off",
             "rules": [{"id": "r1",
                        "clauses": [{"attribute": "country", "operator": "IN", "values": ["UA"], "negate": false}],
                        "variantKey": "on", "rollout": null}],
             "rollout": null}
            """));
    }

    private static HttpResponse<String> post(String path, String json) {
        return send(request(path).POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private static HttpResponse<String> put(String path, String json) {
        return send(request(path).PUT(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(restBase() + path))
            .header("Content-Type", "application/json")
            .header("X-Actor", "integration-test");
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while calling CP", e);
        }
    }

    private static void expect(int status, HttpResponse<String> response) {
        if (response.statusCode() != status) {
            throw new IllegalStateException("Сідинг CP впав: очікував " + status
                + ", отримав " + response.statusCode() + ": " + response.body());
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
