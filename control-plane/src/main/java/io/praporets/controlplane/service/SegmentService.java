package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.SegmentResponse;
import io.praporets.controlplane.api.dto.UpsertSegmentRequest;

import java.util.List;

/**
 * Сегменти середовища (CP-04). Зміна сегмента видима edge → створює ревізію.
 *
 * <p><b>Реалізація (твоя робота):</b> {@code @Service} + {@code @Transactional}.
 */
public class SegmentService {

    /**
     * Сегменти середовища, відсортовані за ключем.
     *
     * @throws NotFoundException якщо середовища немає
     */
    public List<SegmentResponse> list(String environmentKey) {
        throw new UnsupportedOperationException("не реалізовано");
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
    public Upserted<SegmentResponse> upsert(String environmentKey, String segmentKey,
                                            UpsertSegmentRequest request, String actor) {
        throw new UnsupportedOperationException("не реалізовано");
    }
}
