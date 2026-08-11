package io.praporets.edge.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.praporets.grpc.config.v1.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Фейковий control-plane для тестів edge: справжній grpc-java сервер на
 * ефемерному порту. Стрім керований із тестів: кожне підключення —
 * {@link StreamSession}, якій можна веліти слати дельти/heartbeat-и/
 * SnapshotRequired або обірватись.
 *
 * <p>Стан статичний (тести і Quarkus живуть в одній JVM) і скидається в
 * {@link #start()} — кожен тестовий контекст стартує з чистого аркуша.
 */
public class FakeControlPlane implements QuarkusTestResourceLifecycleManager {

    public static final long SNAPSHOT_REVISION = 7;
    public static final String ENVIRONMENT = "dev";
    public static final String FLAG_KEY = "checkout.new-flow";
    public static final String SEGMENT_KEY = "beta-testers";

    private static final AtomicLong snapshotRevision = new AtomicLong(SNAPSHOT_REVISION);
    private static final AtomicInteger snapshotCalls = new AtomicInteger();
    private static final List<StreamSession> sessions = new CopyOnWriteArrayList<>();

    private Server server;

    @Override
    public Map<String, String> start() {
        snapshotRevision.set(SNAPSHOT_REVISION);
        snapshotCalls.set(0);
        sessions.clear();
        try {
            server = ServerBuilder.forPort(0)
                .addService(new FakeConfigService())
                .build()
                .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.of(
            "quarkus.grpc.clients.config.host", "localhost",
            "quarkus.grpc.clients.config.port", String.valueOf(server.getPort()),
            // у LaunchMode TEST Quarkus ІГНОРУЄ port і бере test-port (дефолт —
            // порт власного тестового gRPC-сервера) — без цього рядка клієнт
            // ходить у порожній сервер edge і отримує UNIMPLEMENTED
            "quarkus.grpc.clients.config.test-port", String.valueOf(server.getPort()),
            "quarkus.grpc.clients.config.plain-text", "true",
            "praporets.edge.environment", ENVIRONMENT
        );
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ------------------------------------------------ доступ із тестів

    public static void setSnapshotRevision(long revision) {
        snapshotRevision.set(revision);
    }

    public static int snapshotCalls() {
        return snapshotCalls.get();
    }

    public static int sessionCount() {
        return sessions.size();
    }

    public static StreamSession latestSession() {
        return sessions.getLast();
    }

    /**
     * Чекає, доки кількість підключень стріму досягне {@code count}.
     */
    public static void awaitSessionCount(int count, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (sessions.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        if (sessions.size() < count) {
            throw new AssertionError("Очікував ≥" + count + " стрім-сесій, маю " + sessions.size());
        }
    }

    /**
     * Одне підключення StreamConfig, кероване з тесту.
     */
    public static final class StreamSession {
        private final StreamRequest request;
        private final ServerCallStreamObserver<ConfigUpdate> observer;

        StreamSession(StreamRequest request, ServerCallStreamObserver<ConfigUpdate> observer) {
            this.request = request;
            this.observer = observer;
        }

        public long fromRevision() {
            return request.getFromRevision();
        }

        /**
         * Дельта: наш флаг у стані {@code enabled}, ревізія {@code revision}.
         */
        public void sendDelta(long revision, boolean enabled) {
            observer.onNext(ConfigUpdate.newBuilder()
                .setRevision(revision)
                .setDelta(ConfigDelta.newBuilder().addUpsertedFlags(flagDefinition(enabled)))
                .build());
        }

        public void sendHeartbeat(long revision) {
            observer.onNext(ConfigUpdate.newBuilder()
                .setRevision(revision)
                .setHeartbeat(Heartbeat.newBuilder().setServerTimeMillis(System.currentTimeMillis()))
                .build());
        }

        public void sendSnapshotRequired() {
            observer.onNext(ConfigUpdate.newBuilder()
                .setSnapshotRequired(SnapshotRequired.newBuilder().setReason("test"))
                .build());
            observer.onCompleted();
        }

        /**
         * Аварійний обрив — так виглядає падіння CP для клієнта.
         */
        public void abort() {
            observer.onError(Status.UNAVAILABLE.withDescription("CP died").asRuntimeException());
        }

        public boolean isCancelled() {
            return observer.isCancelled();
        }
    }

    static final class FakeConfigService extends ConfigServiceGrpc.ConfigServiceImplBase {

        @Override
        public void getSnapshot(SnapshotRequest request, StreamObserver<ConfigSnapshot> responseObserver) {
            if (!ENVIRONMENT.equals(request.getEnvironmentKey())) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Environment [" + request.getEnvironmentKey() + "] not found")
                    .asRuntimeException());
                return;
            }
            snapshotCalls.incrementAndGet();
            responseObserver.onNext(cannedSnapshot(snapshotRevision.get()));
            responseObserver.onCompleted();
        }

        @Override
        public void streamConfig(StreamRequest request, StreamObserver<ConfigUpdate> responseObserver) {
            // стрім тримається відкритим; що і коли в нього летить — вирішує тест
            sessions.add(new StreamSession(request, (ServerCallStreamObserver<ConfigUpdate>) responseObserver));
        }

        private static ConfigSnapshot cannedSnapshot(long revision) {
            return ConfigSnapshot.newBuilder()
                .setEnvironmentKey(ENVIRONMENT)
                .setRevision(revision)
                .addFlags(flagDefinition(true))
                .addSegments(SegmentDefinition.newBuilder()
                    .setKey(SEGMENT_KEY)
                    .addClauses(Clause.newBuilder()
                        .setAttribute("plan").setOperator(Operator.IN).addValues("pro")))
                .build();
        }
    }

    private static FlagDefinition flagDefinition(boolean enabled) {
        return FlagDefinition.newBuilder()
            .setKey(FLAG_KEY)
            .setValueType(ValueType.BOOLEAN)
            .setEnabled(enabled)
            .setDefaultVariant("on")
            .setOffVariant("off")
            .addVariants(Variant.newBuilder().setKey("on").setJsonValue("true"))
            .addVariants(Variant.newBuilder().setKey("off").setJsonValue("false"))
            .addRules(Rule.newBuilder()
                .setId("r1")
                .addClauses(Clause.newBuilder()
                    .setAttribute("country").setOperator(Operator.IN).addValues("UA"))
                .setVariantKey("on"))
            .setRollout(Rollout.newBuilder()
                .setSalt("v1")
                .addBuckets(Bucket.newBuilder().setVariantKey("on").setWeight(100_000)))
            .build();
    }
}
