package io.praporets.controlplane.service;

import io.praporets.controlplane.api.dto.FlagConfigResponse;
import io.praporets.controlplane.api.dto.RollbackResponse;
import io.praporets.controlplane.api.dto.SegmentResponse;
import io.praporets.controlplane.domain.EnvironmentRepository;
import io.praporets.controlplane.domain.FlagConfigRepository;
import io.praporets.controlplane.domain.RevisionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Відкат середовища на стару ревізію (CP-06, H4–H6): журнал відтворює старий
 * стан НОВИМИ ревізіями, історія не переписується. Окупність G8 — payload
 * у журналі є повним станом сутності, тож журнал і є бекапом.
 *
 * <p><b>Алгоритм (твоя робота):</b>
 * <ol>
 *   <li>середовище за ключем → немає → {@link NotFoundException}; H6:
 *       {@code toRevision} поза {@code [1, поточна]} →
 *       {@link DomainValidationException} → 400;</li>
 *   <li>усі записи журналу з {@code revision ≤ toRevision}
 *       (новий finder у {@code RevisionLogRepository}, вже додано);</li>
 *   <li>групування за сутністю: {@code FLAG_CONFIG_UPDATED} і
 *       {@code FLAG_TOGGLED} — разом «стан конфіга» за {@code payload.flagKey}
 *       (камінь #5); {@code SEGMENT_UPDATED} — за {@code payload.key}.
 *       З кожної групи береться НАЙПІЗНІШИЙ payload (сортування вже desc —
 *       перший зустрінутий і є найпізніший);</li>
 *   <li>десеріалізація payload: {@code jsonMapper.treeToValue(payload,
 *       FlagConfigResponse.class / SegmentResponse.class)};</li>
 *   <li>відновлення через ЗВИЧАЙНІ сервісні шляхи ({@code FlagConfigService},
 *       {@code SegmentService}) — кожне = нова ревізія + аудит. If-Match
 *       сервісного upsert обходь свідомо: внутрішній виклик знає поточну
 *       версію (напр., прочитай config з БД і передай його
 *       {@code getVersion()} як {@code expectedVersion}); сутності, що
 *       існують у журналі, але ще не в БД, тут неможливі (H5);</li>
 *   <li>сутності, створені ПІСЛЯ {@code toRevision} (немає записів ≤),
 *       не чіпаються — H5, задокументоване обмеження
 *       ({@code docs/backlog.md} → «symmetric deletes for rollback»);</li>
 *   <li>відповідь: {@code rolledBackTo = toRevision}, {@code revision} —
 *       нова поточна (з Environment ПІСЛЯ відновлень; пам'ятай про
 *       write-behind — recorder повертає нову ревізію, можеш взяти max).</li>
 * </ol>
 *
 * <p><b>Аудит ROLLBACK (H4):</b> сервісні шляхи запишуть UPDATE/аудит самі;
 * {@code action=ROLLBACK} додай як хочеш — або окремим підсумковим записом
 * аудиту по середовищу після відновлень, або протягни action через сервіси.
 * Тести пінять лише ревізії і стани, не аудит — рішення твоє.
 *
 * <p><b>ВЕСЬ відкат — одна транзакція</b> ({@code @Transactional} на верхньому
 * методі; сервісні виклики джойняться, камінь #3): або все, або ніщо.
 * Порядок відновлення сутностей недетермінований (мапа) — не покладайся
 * на нього (камінь #4).
 */
@Service
public class RollbackService {

    private final JsonMapper jsonMapper;
    private final EnvironmentRepository environmentRepository;
    private final RevisionLogRepository revisionLogRepository;
    private final FlagConfigRepository flagConfigRepository;
    private final FlagConfigService flagConfigService;
    private final SegmentService segmentService;

    public RollbackService(JsonMapper jsonMapper, EnvironmentRepository environmentRepository,
                           RevisionLogRepository revisionLogRepository, FlagConfigRepository flagConfigRepository,
                           FlagConfigService flagConfigService, SegmentService segmentService) {
        this.jsonMapper = jsonMapper;
        this.environmentRepository = environmentRepository;
        this.revisionLogRepository = revisionLogRepository;
        this.flagConfigRepository = flagConfigRepository;
        this.flagConfigService = flagConfigService;
        this.segmentService = segmentService;
    }

    /**
     * @throws NotFoundException         якщо середовища немає
     * @throws DomainValidationException якщо {@code toRevision} поза
     *                                   {@code [1, поточна]} (H6)
     */
    @Transactional
    public RollbackResponse rollback(String environmentKey, long toRevision, String actor) {
        throw new UnsupportedOperationException("01h: твоя реалізація");
    }
}
