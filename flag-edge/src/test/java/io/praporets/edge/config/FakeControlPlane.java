package io.praporets.edge.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.praporets.grpc.config.v1.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Фейковий control-plane для тестів edge: справжній grpc-java сервер на
 * ефемерному порту з канонічним снапшотом середовища "dev" (ревізія 7).
 * Повертає Quarkus-у конфіг-оверрайди — клієнт "config" підключається сюди
 * замість localhost:9090. Це той самий прийом, що Testcontainers у CP,
 * тільки «контейнер» — легкий in-JVM сервер.
 */
public class FakeControlPlane implements QuarkusTestResourceLifecycleManager {

    public static final long SNAPSHOT_REVISION = 7;
    public static final String ENVIRONMENT = "dev";
    public static final String FLAG_KEY = "checkout.new-flow";
    public static final String SEGMENT_KEY = "beta-testers";

    private Server server;

    @Override
    public Map<String, String> start() {
        try {
            server = ServerBuilder.forPort(0)
                .addService(new FakeConfigService())
                .build()
                .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("[FakeControlPlane] listening on port " + server.getPort());
        return Map.of(
            "quarkus.grpc.clients.config.host", "localhost",
            "quarkus.grpc.clients.config.port", String.valueOf(server.getPort()),
            // у LaunchMode TEST Quarkus ІГНОРУЄ port і бере test-port (дефолт —
            // порт власного тестового gRPC-сервера, 9001) — без цього рядка
            // клієнт ходить у порожній сервер edge і отримує UNIMPLEMENTED
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

    static final class FakeConfigService extends ConfigServiceGrpc.ConfigServiceImplBase {

        @Override
        public void getSnapshot(SnapshotRequest request, StreamObserver<ConfigSnapshot> responseObserver) {
            if (!ENVIRONMENT.equals(request.getEnvironmentKey())) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Environment [" + request.getEnvironmentKey() + "] not found")
                    .asRuntimeException());
                return;
            }
            responseObserver.onNext(cannedSnapshot());
            responseObserver.onCompleted();
        }
        // streamConfig свідомо не перевизначено: 02c стрім не чіпає, а дефолтний
        // UNIMPLEMENTED одразу викриє, якщо loader раптом полізе у стрім

        private static ConfigSnapshot cannedSnapshot() {
            return ConfigSnapshot.newBuilder()
                .setEnvironmentKey(ENVIRONMENT)
                .setRevision(SNAPSHOT_REVISION)
                .addFlags(FlagDefinition.newBuilder()
                    .setKey(FLAG_KEY)
                    .setValueType(ValueType.BOOLEAN)
                    .setEnabled(true)
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
                        .addBuckets(Bucket.newBuilder().setVariantKey("on").setWeight(100_000))))
                .addSegments(SegmentDefinition.newBuilder()
                    .setKey(SEGMENT_KEY)
                    .addClauses(Clause.newBuilder()
                        .setAttribute("plan").setOperator(Operator.IN).addValues("pro")))
                .build();
        }
    }
}
