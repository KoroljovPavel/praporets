package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.Environment;
import io.praporets.controlplane.domain.FlagConfigRepository;
import io.praporets.controlplane.domain.SegmentRepository;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Повний зліпок середовища для {@code GetSnapshot} (E-01: edge вантажить його
 * на старті). gRPC-аналог {@code EnvironmentConfigAssembler} з 01h, але:
 * ВСІ флаги середовища (не один) і на виході proto, не core-модель — снапшот
 * містить і те, чого ядру не треба ({@code value_type}, variants як довідник).
 *
 * <p><b>Реалізація (твоя робота):</b>
 * <ol>
 *   <li>{@code flagConfigRepository.findAllByEnvironmentKey} — метод уже
 *       оголошено з {@code @EntityGraph} (flag + flag.variants), інакше
 *       мапінг кожного флага стріляв би N+1 по variants;</li>
 *   <li>{@code segmentRepository.findAllByEnvironmentKey} — всі сегменти;</li>
 *   <li>усе через {@link ConfigProtoMapper} у {@code ConfigSnapshot} з
 *       {@code environment_key} і {@code revision = environment.getRevision()}.</li>
 * </ol>
 *
 * <p>{@code readOnly}-транзакція обгортає обидва запити: снапшот атомарний
 * відносно паралельних змін — ревізія і вміст із одного знімка БД (MVCC).
 * Викликач (gRPC-сервіс) сам перевіряє існування середовища.
 */
@Component
public class ConfigSnapshotAssembler {

    private final ConfigProtoMapper mapper;
    private final SegmentRepository segmentRepository;
    private final FlagConfigRepository flagConfigRepository;

    public ConfigSnapshotAssembler(ConfigProtoMapper mapper,
                                   SegmentRepository segmentRepository,
                                   FlagConfigRepository flagConfigRepository) {
        this.mapper = mapper;
        this.segmentRepository = segmentRepository;
        this.flagConfigRepository = flagConfigRepository;
    }

    @Transactional(readOnly = true)
    public ConfigSnapshot assemble(Environment environment) {
        throw new UnsupportedOperationException("02b: твоя реалізація");
    }
}
