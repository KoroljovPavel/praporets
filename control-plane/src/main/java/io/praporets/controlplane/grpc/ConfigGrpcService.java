package io.praporets.controlplane.grpc;

import io.grpc.stub.StreamObserver;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.grpc.config.v1.ConfigServiceGrpc;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import io.praporets.grpc.config.v1.ConfigUpdate;
import io.praporets.grpc.config.v1.SnapshotRequest;
import io.praporets.grpc.config.v1.StreamRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * gRPC-реалізація {@code praporets.config.v1.ConfigService} (CP-08).
 * Spring gRPC сам знаходить бін типу {@code BindableService} і біндить його
 * на сервер (порт — {@code spring.grpc.server.port}) — жодних анотацій
 * реєстрації не треба.
 *
 * <p><b>Правило транспортного шару:</b> з gRPC-методів НЕ кидати винятки —
 * рантайм перетворить їх на невиразний {@code UNKNOWN}. Всі помилки — через
 * {@code responseObserver.onError(Status.XXX.withDescription(...)
 * .asRuntimeException())}. Невідоме середовище → {@code NOT_FOUND} (аналог
 * 404 із 01g).
 *
 * <p><b>{@code getSnapshot} (твоя реалізація):</b> знайти середовище
 * (нема → NOT_FOUND) → {@link ConfigSnapshotAssembler#assemble} →
 * {@code onNext} + {@code onCompleted}.
 *
 * <p><b>{@code streamConfig} (твоя реалізація), порядок критичний:</b>
 * <ol>
 *   <li>середовище: нема → {@code onError(NOT_FOUND)}, кінець;</li>
 *   <li>{@code gap = environment.getRevision() - request.getFromRevision()}.
 *       Якщо {@code gap > revisionWindow} <b>або</b> {@code gap < 0} (edge
 *       «з майбутнього» — БД відкотили) → {@code onNext(SnapshotRequired)} +
 *       {@code onCompleted}, БЕЗ реєстрації в реєстрі: edge зробить
 *       {@code GetSnapshot} і перепідключиться з новим fromRevision (02d);</li>
 *   <li>інакше — повісити прибирання: кастнути observer до
 *       {@code ServerCallStreamObserver<ConfigUpdate>} і
 *       {@code setOnCancelHandler(() -> registry.deregister(...))} — клієнт
 *       зник/дедлайн → стрім не тече вічно (камінь: хендлер ставиться ДО
 *       першого onNext, інакше гонка з відміною);</li>
 *   <li>{@code registry.register(environmentKey, observer)} — <b>спершу</b>
 *       реєстрація, <b>потім</b> catch-up. Якщо навпаки — зміна, закомічена
 *       між збиранням catch-up і реєстрацією, загубиться назавжди. У
 *       прийнятому порядку вона може приїхати двічі (у catch-up і live-пушем)
 *       — це безпечно: дельти є upsert-ами поточного стану, edge застосує
 *       ідемпотентно;</li>
 *   <li>catch-up: {@code gap == 0} → нічого; інакше
 *       {@code onNext(ConfigUpdate{revision = поточна,
 *       delta = deltaAssembler.assembleSince(env, fromRevision)})}.
 *       {@code onNext} — під {@code synchronized (observer)}: live-push із
 *       реєстру вже може конкурувати (та сама дисципліна, що в
 *       {@link ConfigStreamRegistry#publish});</li>
 *   <li>{@code onCompleted} НЕ звати — стрім живе, доки edge підключений;
 *       далі йому пишуть тільки publisher і heartbeat.</li>
 * </ol>
 */
@Service
public class ConfigGrpcService extends ConfigServiceGrpc.ConfigServiceImplBase {

    private final long revisionWindow;
    private final DeltaAssembler deltaAssembler;
    private final ConfigStreamRegistry registry;
    private final ConfigSnapshotAssembler snapshotAssembler;
    private final EnvironmentRepository environmentRepository;

    public ConfigGrpcService(DeltaAssembler deltaAssembler,
                             ConfigStreamRegistry registry,
                             ConfigSnapshotAssembler snapshotAssembler,
                             EnvironmentRepository environmentRepository,
                             @Value("${praporets.grpc.revision-window:500}") long revisionWindow) {
        this.registry = registry;
        this.deltaAssembler = deltaAssembler;
        this.revisionWindow = revisionWindow;
        this.snapshotAssembler = snapshotAssembler;
        this.environmentRepository = environmentRepository;
    }

    @Override
    public void getSnapshot(SnapshotRequest request, StreamObserver<ConfigSnapshot> responseObserver) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }

    @Override
    public void streamConfig(StreamRequest request, StreamObserver<ConfigUpdate> responseObserver) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }
}
