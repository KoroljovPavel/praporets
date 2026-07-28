package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.SegmentResponse;
import io.praporets.controlplane.api.dto.UpsertSegmentRequest;
import io.praporets.controlplane.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/**
 * Сегменти середовища (CP-04). Зміна сегмента видима edge → створює ревізію.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service} + {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class SegmentService {

    private final JsonMapper jsonMapper;
    private final RevisionRecorder revisionRecorder;
    private final SegmentRepository segmentRepository;
    private final EnvironmentRepository environmentRepository;

    public SegmentService(SegmentRepository segmentRepository, EnvironmentRepository environmentRepository,
                          RevisionRecorder revisionRecorder, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.revisionRecorder = revisionRecorder;
        this.segmentRepository = segmentRepository;
        this.environmentRepository = environmentRepository;
    }

    /**
     * Сегменти середовища, відсортовані за ключем.
     *
     * @throws NotFoundException якщо середовища немає
     */
    public List<SegmentResponse> list(String environmentKey) {
        return segmentRepository.findAllByEnvironmentKey(environmentKey).stream().map(s -> new SegmentResponse(
            s.getId(), s.getKey(), s.getConditions(), s.getVersion()
        )).toList();
    }

    /**
     * Створює або повністю замінює сегмент. В ОДНІЙ транзакції з самою зміною:
     * {@link RevisionRecorder#recordChange} ({@code SEGMENT_UPDATED}, payload —
     * {@code valueToTree(response)}) + {@link RevisionRecorder#audit}
     * ({@code CREATE}/{@code UPDATE}, {@code entityType=SEGMENT}).
     * If-Match тут свідомо не вимагаємо (сегменти правляться рідко;
     * last-write-wins прийнятний — зафіксовано в G4/G5).
     *
     * @throws NotFoundException якщо середовища немає
     */
    @Transactional
    public Upserted<SegmentResponse> upsert(String environmentKey, String segmentKey,
                                            UpsertSegmentRequest request, String actor) {
        Environment environment = environmentRepository.findByKey(environmentKey).orElseThrow(() -> new NotFoundException(
            "Entity with key [" + environmentKey + "] not found"
        ));
        Optional<Segment> optionalSegment = segmentRepository.findByEnvironmentKeyAndKey(environmentKey, segmentKey);
        boolean isNew = optionalSegment.isEmpty();

        Segment segment = optionalSegment.orElseGet(() -> new Segment(environment, segmentKey, request.conditions()));
        Optional<SegmentResponse> prevResponse = Optional.empty();
        if (isNew) {
            segment = segmentRepository.save(segment);
        } else {
            prevResponse = Optional.of(new SegmentResponse(segment.getId(), segment.getKey(), segment.getConditions(), segment.getVersion()));
            segment.setConditions(request.conditions());
        }

        segmentRepository.flush();

        SegmentResponse response = new SegmentResponse(segment.getId(), segment.getKey(), segment.getConditions(), segment.getVersion());

        revisionRecorder.recordChange(environmentKey, ChangeType.SEGMENT_UPDATED, jsonMapper.valueToTree(response));
        revisionRecorder.audit(actor, isNew ? "CREATE" : "UPDATE", "SEGMENT", segment.getId(),
            prevResponse.<JsonNode>map(jsonMapper::valueToTree).orElse(null), jsonMapper.valueToTree(response));

        return new Upserted<>(response, isNew);
    }
}
