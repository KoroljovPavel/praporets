package io.praporets.e2e;

import io.cucumber.java.PendingException;
import io.cucumber.java.uk.Дано;
import io.cucumber.java.uk.Коли;
import io.cucumber.java.uk.Тоді;

/**
 * Кроки {@code concurrent-edits.feature}: оптимістичне блокування не дає
 * загубити оновлення (03e, сценарій 3). Єдиний сценарій, що живе цілком у
 * CP REST — edge-репліки участі не беруть.
 *
 * <p><b>Стан між кроками:</b> додай поля сам (версія конфігурації на момент
 * читання, тіло/відповідь другого оператора).
 *
 * <p><b>«Зміна» оператора — консервативна:</b> міняй лише {@code values}
 * правила r1 (напр. перший: {@code ["UA", "PL"]}, другий: {@code ["UA", "DE"]}),
 * решту тіла конфігурації бери 1:1 із прочитаного GET-ом. Так сценарій не
 * ламає baseline інших сценаріїв (enabled/variants/defaultVariant незмінні),
 * а «чия зміна вижила» видно по values.
 *
 * <p><b>Контракт CP</b> (01g/01h, {@code FlagConfigController} +
 * {@code ApiExceptionHandler}): {@code GET .../config} → 200 із {@code version};
 * {@code PUT} з {@code If-Match: <version>} → 200, версія +1; {@code PUT} зі
 * СТАРОЮ версією → 409, тіло — RFC 9457 ProblemDetail
 * ({@code Content-Type: application/problem+json}), БД не змінена.
 */
public class ConcurrentEditsSteps {

    /**
     * {@link E2eStack#ensureStarted()}; {@link E2eStack#getConfig()} → 200,
     * запам'ятай у поля {@code version} і тіло конфігурації — обидва
     * «оператори» стартують з однієї й тієї самої прочитаної версії
     * (симуляція двох відкритих форм редагування).
     */
    @Дано("два оператори завантажили конфігурацію флага {string} однієї версії")
    public void два_оператори_завантажили_конфігурацію(String flagKey) {
        // TODO: реалізуй крок (GET config, зафіксуй version + тіло)
        throw new PendingException();
    }

    /**
     * Перший оператор перемагає: {@link E2eStack#putConfig putConfig(тілоА,
     * прочитана версія)} → асерт 200 (не 201 — конфігурація вже існує).
     * Тіло А — прочитана конфігурація зі зміненими values правила r1
     * (див. JavaDoc класу).
     */
    @Коли("перший оператор зберігає свою зміну")
    public void перший_оператор_зберігає() {
        // TODO: реалізуй крок (PUT з If-Match = прочитана версія → 200)
        throw new PendingException();
    }

    /**
     * Другий оператор із ТІЄЮ САМОЮ (уже застарілою) версією: {@code putConfig(тілоB,
     * та сама версія)} — відповідь збережи в поле, БЕЗ асерта тут (статус
     * перевіряє наступний крок; цей крок — лише дія).
     */
    @Коли("другий оператор зберігає іншу зміну з тією самою версією")
    public void другий_оператор_зберігає_з_тією_самою_версією() {
        // TODO: реалізуй крок (PUT зі старою версією, відповідь у поле)
        throw new PendingException();
    }

    /**
     * Відповідь другого оператора: статус 409, {@code Content-Type:
     * application/problem+json}, у тілі ProblemDetail поле {@code status} == 409
     * (формат помилок — контракт, не самописний JSON).
     */
    @Тоді("запит другого оператора відхилено з конфліктом")
    public void запит_другого_відхилено_з_конфліктом() {
        // TODO: реалізуй крок (409 + problem+json)
        throw new PendingException();
    }

    /**
     * Lost update НЕ стався: {@code getConfig()} → у values правила r1 —
     * зміна ПЕРШОГО оператора (і жодного сліду зміни другого), а
     * {@code version} == прочитана на початку + 1 (рівно один успішний запис).
     */
    @Тоді("збереженою лишається зміна першого оператора")
    public void збереженою_лишається_зміна_першого() {
        // TODO: реалізуй крок (GET: values першого, version == стара + 1)
        throw new PendingException();
    }
}
