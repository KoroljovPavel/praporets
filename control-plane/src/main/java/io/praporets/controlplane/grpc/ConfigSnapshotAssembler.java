package io.praporets.controlplane.grpc;

import io.praporets.controlplane.domain.*;
import io.praporets.grpc.config.v1.ConfigSnapshot;
import io.praporets.grpc.config.v1.FlagDefinition;
import io.praporets.grpc.config.v1.SegmentDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private final ConfigProtoMapper configProtoMapper;
    private final SegmentRepository segmentRepository;
    private final FlagConfigRepository flagConfigRepository;

    public ConfigSnapshotAssembler(ConfigProtoMapper configProtoMapper,
                                   SegmentRepository segmentRepository,
                                   FlagConfigRepository flagConfigRepository) {
        this.configProtoMapper = configProtoMapper;
        this.segmentRepository = segmentRepository;
        this.flagConfigRepository = flagConfigRepository;
    }

    @Transactional(readOnly = true)
    public ConfigSnapshot assemble(Environment environment) {

        List<FlagConfig> flagConfig = flagConfigRepository.findAllByEnvironmentKey(environment.getKey());
        List<Segment> segments = segmentRepository.findAllByEnvironmentKey(environment.getKey());

        List<FlagDefinition> flagDefinition = flagConfig.stream().map(configProtoMapper::toFlag).toList();
        List<SegmentDefinition> segmentDefinitions = segments.stream().map(configProtoMapper::toSegment).toList();

        return ConfigSnapshot.newBuilder()
                .setEnvironmentKey(environment.getKey())
                .setRevision(environment.getRevision())
                .addAllFlags(flagDefinition)
                .addAllSegments(segmentDefinitions)
                .build();
    }
}
