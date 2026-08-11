package io.praporets.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
// Testcontainers 2.x: канонічний пакет (о.т.containers.PostgreSQLContainer — deprecated alias)
import org.testcontainers.postgresql.PostgreSQLContainer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Спільний Testcontainers-стек для трьох BDD-сценаріїв: усе у контейнерах,
 * включно з {@value #EDGE_COUNT} edge-репліками (fast-jar
 * {@code flag-edge/build/quarkus-app} на eclipse-temurin:25-jre; шляхи до
 * артефактів CP і edge передає Gradle через system properties
 * {@code praporets.cp.jar} і {@code praporets.edge.app}).
 *
 * <p><b>Життєвий цикл:</b> стек стартує ЛІНИВО з першого Given-кроку через
 * {@link #ensureStarted()} (ідемпотентний, один стек на JVM-прогін) і живе
 * до кінця прогону — сценарії шарять його послідовно, кожен Given-крок
 * відновлює свій baseline. Прибирання — Ryuk (Testcontainers) при виході JVM.
 *
 * <p><b>Топологія мережі:</b> Postgres ({@code postgres:5432}) + Kafka
 * ({@code kafka:19092}) + CP (alias {@code control-plane}, REST 8080 /
 * gRPC 9090) + {@value #EDGE_COUNT} edge (unified 8081). Edge-репліки
 * знаходять CP за alias-ом усередині мережі; кроки говорять із CP REST і
 * edge REST/metrics через замаплені host-порти. <b>Host-порт CP REST
 * фіксований</b> — переживає stop/start у сценарії падіння.
 *
 * <p><b>Сідинг:</b> environment {@value #ENVIRONMENT},
 * флаг {@value #FLAG_KEY} (BOOLEAN, variants on=true/off=false), config:
 * enabled, правило {@code r1: country IN [UA] → on}, без rollout. Edge-и
 * стартують ПІСЛЯ сідингу — readiness чекає перший снапшот.
 */
public final class E2eStack {

    public static final String ENVIRONMENT = "dev";
    public static final String FLAG_KEY = "checkout.new-flow";
    public static final int EDGE_COUNT = 3;
    /**
     * Гейдж віку конфігурації на edge (метрика {@code SyncMetrics}).
     */
    public static final String STALENESS_METRIC = "flag_edge_config_staleness_seconds";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static boolean started;
    private static Throwable startFailure;
    private static Network network;
    private static PostgreSQLContainer postgres;
    private static KafkaContainer kafka;
    private static GenericContainer<?> controlPlane;
    private static int cpRestPort;
    private static final List<GenericContainer<?>> edges = new ArrayList<>();

    private E2eStack() {
    }

    /**
     * Піднімає стек, якщо ще не піднятий: мережа → Postgres → Kafka → CP →
     * сідинг → {@value #EDGE_COUNT} edge-репліки. Блокує до готовності всіх
     * (CP: {@code /actuator/health}; edge: {@code /q/health/ready} — UP лише
     * після завантаженого снапшота). Повторні виклики — no-op. Невдалий старт —
     * fail-fast: перший виняток запам'ятовується, наступні сценарії падають
     * одразу з ним як причиною, без каскаду повторних стартів поверх
     * напівживих контейнерів.
     */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        if (startFailure != null) {
            throw new IllegalStateException("стек не стартував — причина в першому падінні нижче", startFailure);
        }
        try {
            doStart();
            started = true;
        } catch (RuntimeException | Error e) {
            startFailure = e;
            throw e;
        }
    }

    private static void doStart() {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withDatabaseName("praporets")
            .withUsername("praporets")
            .withPassword("praporets");
        postgres.start();

        // додатковий listener kafka:19092 для контейнерів цієї ж мережі
        kafka = new KafkaContainer("apache/kafka:4.1.0")
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092");
        kafka.start();

        cpRestPort = freePort();
        controlPlane = buildControlPlaneContainer();
        controlPlane.start();

        seed();

        for (int i = 0; i < EDGE_COUNT; i++) {
            GenericContainer<?> edge = buildEdgeContainer();
            edge.start();
            edges.add(edge);
        }
    }

    private static GenericContainer<?> buildControlPlaneContainer() {
        String jarPath = System.getProperty("praporets.cp.jar");
        if (jarPath == null) {
            throw new IllegalStateException(
                "system property praporets.cp.jar не заданий — тест запущено повз Gradle? "
                    + "(:e2e:test прокидає шлях до bootJar)");
        }
        GenericContainer<?> cp = new GenericContainer<>("eclipse-temurin:25-jre")
            .withNetwork(network)
            .withNetworkAliases("control-plane")
            .withCopyFileToContainer(MountableFile.forHostPath(jarPath), "/app/cp.jar")
            .withCommand("java", "-jar", "/app/cp.jar")
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/praporets")
            .withEnv("SPRING_DATASOURCE_USERNAME", "praporets")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "praporets")
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8080)
                .withStartupTimeout(Duration.ofMinutes(2)));
        // фіксований host-порт REST: переживає stop()/start() контейнера
        cp.setPortBindings(List.of(cpRestPort + ":8080"));
        return cp;
    }

    private static GenericContainer<?> buildEdgeContainer() {
        String appDir = System.getProperty("praporets.edge.app");
        if (appDir == null) {
            throw new IllegalStateException(
                "system property praporets.edge.app не заданий — тест запущено повз Gradle? "
                    + "(:e2e:test прокидає шлях до quarkus-app)");
        }
        return new GenericContainer<>("eclipse-temurin:25-jre")
            .withNetwork(network)
            .withCopyFileToContainer(MountableFile.forHostPath(appDir), "/app")
            .withCommand("java", "-jar", "/app/quarkus-run.jar")
            .withEnv("QUARKUS_GRPC_CLIENTS_CONFIG_HOST", "control-plane")
            .withEnv("QUARKUS_GRPC_CLIENTS_CONFIG_PORT", "9090")
            .withEnv("QUARKUS_GRPC_CLIENTS_CONFIG_PLAIN_TEXT", "true")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
            .withEnv("PRAPORETS_EDGE_ENVIRONMENT", ENVIRONMENT)
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/q/health/ready").forPort(8081)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    // ------------------------------------------------ керування стеком

    /**
     * База CP REST, напр. {@code http://localhost:54321}.
     */
    public static String cpRestBase() {
        return "http://localhost:" + cpRestPort;
    }

    /**
     * База REST/metrics edge-репліки {@code replica} (0..{@value #EDGE_COUNT}-1).
     */
    public static String edgeRestBase(int replica) {
        GenericContainer<?> edge = edges.get(replica);
        return "http://" + edge.getHost() + ":" + edge.getMappedPort(8081);
    }

    /**
     * Зупиняє CP. Postgres, Kafka і edge-репліки живі — data plane працює далі,
     * стан переживає падіння.
     */
    public static void stopControlPlane() {
        controlPlane.stop();
    }

    /**
     * Піднімає CP заново на тому самому host-порту і з тим самим network-alias
     * {@code control-plane} — edge-репліки знайдуть його за DNS при reconnect-і.
     */
    public static void startControlPlane() {
        controlPlane = buildControlPlaneContainer();
        controlPlane.start();
    }

    // ------------------------------------------------ дії оператора (CP REST)

    /**
     * Перемикає enabled флага {@value #FLAG_KEY} — той самий шлях, що в оператора.
     */
    public static void toggleFlag(boolean enabled) {
        HttpResponse<String> response = post(
            cpRestBase() + "/api/v1/environments/" + ENVIRONMENT + "/flags/" + FLAG_KEY + "/toggle",
            "{\"enabled\": " + enabled + "}");
        if (response.statusCode() != 200) {
            throw new IllegalStateException("toggle → " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * {@code GET .../config} флага {@value #FLAG_KEY}. Тіло — JSON із
     * {@code version} (для If-Match), {@code rules}, {@code enabled} тощо.
     */
    public static HttpResponse<String> getConfig() {
        return send(request(cpRestBase() + configPath()).GET().build());
    }

    /**
     * {@code PUT .../config} з заголовком {@code If-Match: ifMatchVersion}.
     * НЕ перевіряє статус — конфліктний сценарій якраз і розглядає 200 vs 409;
     * асерти на статус/ProblemDetail — справа кроків.
     */
    public static HttpResponse<String> putConfig(String json, long ifMatchVersion) {
        return send(request(cpRestBase() + configPath())
            .header("If-Match", String.valueOf(ifMatchVersion))
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build());
    }

    private static String configPath() {
        return "/api/v1/environments/" + ENVIRONMENT + "/flags/" + FLAG_KEY + "/config";
    }

    /**
     * Поточна ревізія середовища прямо з Postgres — незалежне від CP джерело
     * правди для assert-ів (працює і коли CP лежить).
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

    // ------------------------------------------------ спостереження за edge

    /**
     * REST-обчислення {@value #FLAG_KEY} на репліці {@code replica} для
     * user-42 із атрибутом {@code country}. Повертає розібраний JSON відповіді:
     * {@code flagKey, variantKey, jsonValue, reason, ruleId, revision}.
     *
     * @throws IllegalStateException якщо статус не 200 (edge зобов'язаний
     *                               відповідати 200 навіть на невідомий флаг)
     */
    public static JsonNode edgeEvaluate(int replica, String country) {
        String body = """
            {
              "environmentKey": "%s",
              "flagKey": "%s",
              "context": {"userKey": "user-42", "attributes": {"country": "%s"}}
            }
            """.formatted(ENVIRONMENT, FLAG_KEY, country);
        HttpResponse<String> response = post(edgeRestBase(replica) + "/api/v1/evaluate", body);
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "edge[" + replica + "] evaluate → " + response.statusCode() + ": " + response.body());
        }
        return json(response.body());
    }

    /**
     * Значення метрики з {@code /q/metrics} репліки {@code replica} у
     * Prometheus-форматі; {@code metricName} — початок рядка, напр.
     * {@link #STALENESS_METRIC}.
     *
     * @throws IllegalStateException якщо метрики немає у виводі
     */
    public static double edgeMetric(int replica, String metricName) {
        HttpResponse<String> response = send(request(edgeRestBase(replica) + "/q/metrics").GET().build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("edge[" + replica + "] metrics → " + response.statusCode());
        }
        return response.body().lines()
            .filter(line -> line.startsWith(metricName))
            .findFirst()
            .map(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
            .orElseThrow(() -> new IllegalStateException(
                "метрика " + metricName + " відсутня у /q/metrics edge[" + replica + "]"));
    }

    // ------------------------------------------------ утиліти для кроків

    /**
     * Парсить JSON-рядок (обгортка над Jackson без checked-винятків).
     */
    public static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException("не JSON: " + body, e);
        }
    }

    /**
     * Чекає умову до {@code timeoutMillis} (крок 50мс); не дочекалась —
     * {@link AssertionError} з поясненням {@code what}.
     */
    public static void await(String what, long timeoutMillis, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("перерваний під час очікування: " + what, e);
            }
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError(what + ": умова не настала за " + timeoutMillis + "мс");
        }
    }

    // ------------------------------------------------ нутрощі

    private static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void seed() {
        expect(201, post(cpRestBase() + "/api/v1/environments",
            "{\"key\": \"" + ENVIRONMENT + "\", \"name\": \"Dev\"}"));
        expect(201, post(cpRestBase() + "/api/v1/flags", """
            {"key": "%s", "name": "New checkout flow", "valueType": "BOOLEAN",
             "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]}
            """.formatted(FLAG_KEY)));
        // перший upsert конфігурації = створення → 201, If-Match не потрібен
        expect(201, send(request(cpRestBase() + configPath())
            .PUT(HttpRequest.BodyPublishers.ofString("""
                {"enabled": true, "defaultVariant": "on", "offVariant": "off",
                 "rules": [{"id": "r1",
                            "clauses": [{"attribute": "country", "operator": "IN", "values": ["UA"], "negate": false}],
                            "variantKey": "on", "rollout": null}],
                 "rollout": null}
                """))
            .build()));
    }

    private static HttpResponse<String> post(String url, String json) {
        return send(request(url).POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-Actor", "e2e");
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while calling stack", e);
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
