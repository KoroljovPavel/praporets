package io.praporets.controlplane.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.grpc.config.v1.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * gRPC-реалізація {@code praporets.config.v1.ConfigService} — вхідна точка
 * edge-сервісів: разовий {@code GetSnapshot} і довгоживучий server-streaming
 * {@code StreamConfig}. Spring gRPC сам знаходить бін типу
 * {@code BindableService} і біндить його на сервер (порт —
 * {@code spring.grpc.server.port}) — жодних анотацій реєстрації не треба.
 *
 * <p><b>Правило транспортного шару:</b> з gRPC-методів не кидаються
 * винятки — рантайм перетворив би їх на невиразний {@code UNKNOWN}. Всі
 * помилки йдуть через {@code responseObserver.onError(Status...)};
 * невідоме середовище → {@code NOT_FOUND}.
 *
 * <p><b>{@code streamConfig} — порядок кроків критичний:</b>
 * <ol>
 *   <li>{@code gap = поточна ревізія - fromRevision}. Якщо
 *       {@code gap > revisionWindow} <b>або</b> {@code gap < 0} (edge
 *       «з майбутнього» — наприклад, БД відкотили) →
 *       {@code SnapshotRequired} + {@code onCompleted}, без реєстрації в
 *       реєстрі: edge зробить {@code GetSnapshot} і перепідключиться з новим
 *       fromRevision;</li>
 *   <li>інакше — {@code setOnCancelHandler} із дереєстрацією ставиться ДО
 *       першого onNext (інакше гонка з відміною): клієнт зник або спрацював
 *       дедлайн → стрім не тече вічно;</li>
 *   <li><b>спершу</b> реєстрація в {@link ConfigStreamRegistry},
 *       <b>потім</b> catch-up-дельта. Якщо навпаки — зміна, закомічена між
 *       збиранням catch-up і реєстрацією, загубилася б назавжди. У прийнятому
 *       порядку вона може приїхати двічі (у catch-up і live-пушем) — це
 *       безпечно: дельти є upsert-ами поточного стану, edge застосовує їх
 *       ідемпотентно;</li>
 *   <li>catch-up надсилається лише при {@code gap != 0}, через
 *       {@link ConfigStreamRegistry#send} — той серіалізує onNext із
 *       конкурентним live-пушем;</li>
 *   <li>{@code onCompleted} не викликається — стрім живе, доки edge
 *       підключений; далі в нього пишуть тільки fan-out і heartbeat.</li>
 * </ol>
 */
@Service
public class ConfigGrpcService extends ConfigServiceGrpc.ConfigServiceImplBase {

    private final long revisionWindow;
    private final DeltaAssembler deltaAssembler;
    private final ConfigStreamRegistry configStreamRegistry;
    private final ConfigSnapshotAssembler snapshotAssembler;
    private final EnvironmentRepository environmentRepository;

    public ConfigGrpcService(DeltaAssembler deltaAssembler,
                             ConfigStreamRegistry configStreamRegistry,
                             ConfigSnapshotAssembler snapshotAssembler,
                             EnvironmentRepository environmentRepository,
                             @Value("${praporets.grpc.revision-window:500}") long revisionWindow) {
        this.deltaAssembler = deltaAssembler;
        this.revisionWindow = revisionWindow;
        this.snapshotAssembler = snapshotAssembler;
        this.configStreamRegistry = configStreamRegistry;
        this.environmentRepository = environmentRepository;
    }

    @Override
    public void getSnapshot(SnapshotRequest request, StreamObserver<ConfigSnapshot> responseObserver) {
        environmentRepository.findByKey(request.getEnvironmentKey()).ifPresentOrElse(environment -> {
            ConfigSnapshot snapshot = snapshotAssembler.assemble(environment);
            responseObserver.onNext(snapshot);
            responseObserver.onCompleted();
        }, () -> responseObserver.onError(Status.NOT_FOUND
            .withDescription("Environment [" + request.getEnvironmentKey() + "] not found")
            .asRuntimeException()));
    }

    @Override
    public void streamConfig(StreamRequest request, StreamObserver<ConfigUpdate> responseObserver) {
        environmentRepository.findByKey(request.getEnvironmentKey()).ifPresentOrElse(
            environment -> {
                long gap = environment.getRevision() - request.getFromRevision();
                if (gap > revisionWindow || gap < 0) {
                    String reason = String.format(
                        "Revision gap is out of window [window=%d, requested=%d, current=%d, gap=%d]",
                        revisionWindow, request.getFromRevision(), environment.getRevision(), gap
                    );
                    responseObserver.onNext(ConfigUpdate.newBuilder()
                        .setSnapshotRequired(SnapshotRequired.newBuilder()
                            .setReason(reason)
                            .build())
                        .build());
                    responseObserver.onCompleted();
                } else {
                    ServerCallStreamObserver<ConfigUpdate> serverCallStreamObserver = (ServerCallStreamObserver<ConfigUpdate>) responseObserver;
                    serverCallStreamObserver.setOnCancelHandler(() -> configStreamRegistry.deregister(environment.getKey(), responseObserver));

                    configStreamRegistry.register(environment.getKey(), responseObserver);
                    if (gap != 0) {
                        ConfigDelta configDelta = deltaAssembler.assembleSince(environment.getKey(), request.getFromRevision());
                        configStreamRegistry.send(environment.getKey(), responseObserver, ConfigUpdate.newBuilder()
                            .setRevision(environment.getRevision())
                            .setDelta(configDelta)
                            .build());
                    }
                }
            },
            () -> responseObserver.onError(Status.NOT_FOUND
                .withDescription("Environment [" + request.getEnvironmentKey() + "] not found")
                .asRuntimeException())
        );
    }
}
