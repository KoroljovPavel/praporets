package io.praporets.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.uk.Дано;
import io.cucumber.java.uk.Коли;
import io.cucumber.java.uk.Тоді;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кроки {@code concurrent-edits.feature}: оптимістичне блокування не дає
 * загубити оновлення. Єдиний сценарій, що живе цілком у CP REST —
 * edge-репліки участі не беруть.
 *
 * <p><b>Стан між кроками:</b> поля тримають версію конфігурації на момент
 * читання, прочитане тіло і відповідь другого оператора.
 *
 * <p><b>«Зміна» оператора — консервативна:</b> міняються лише {@code values}
 * правила r1 (перший: {@code ["UA", "PL"]}, другий: {@code ["UA", "DE"]}),
 * решта тіла конфігурації береться 1:1 із прочитаного GET-ом. Так сценарій
 * не ламає baseline інших сценаріїв на спільному стеку
 * (enabled/variants/defaultVariant незмінні), а «чия зміна вижила» видно
 * по values.
 *
 * <p><b>Контракт CP</b> ({@code FlagConfigController} +
 * {@code ApiExceptionHandler}): {@code GET .../config} → 200 із {@code version};
 * {@code PUT} з {@code If-Match: <version>} → 200, версія +1; {@code PUT} зі
 * СТАРОЮ версією → 409, тіло — RFC 9457 ProblemDetail
 * ({@code Content-Type: application/problem+json}), БД не змінена.
 */
public class ConcurrentEditsSteps {

    private long version = 0L;
    private JsonNode configNode;

    private HttpResponse<String> secondResponse;

    /**
     * Піднімає стек, читає конфігурацію ({@link E2eStack#getConfig()} → 200)
     * і запам'ятовує {@code version} та тіло — обидва «оператори» стартують
     * з однієї й тієї самої прочитаної версії (симуляція двох відкритих
     * форм редагування).
     */
    @Дано("два оператори завантажили конфігурацію флага {string} однієї версії")
    public void два_оператори_завантажили_конфігурацію(String flagKey) {
        assertThat(flagKey).as("Ключ прапора повинен співпадати з тестовим").isEqualTo(E2eStack.FLAG_KEY);

        E2eStack.ensureStarted();
        HttpResponse<String> response = E2eStack.getConfig();

        assertThat(response.statusCode())
            .as("GET config мав повернути 200; тіло: %s", response.body())
            .isEqualTo(200);

        configNode = E2eStack.json(response.body());
        version = configNode.get("version").asLong();

    }

    /**
     * Перший оператор перемагає: PUT із прочитаною версією → guard-асерт
     * 200 (не 201 — конфігурація вже існує). Тіло — прочитана конфігурація
     * зі зміненими values правила r1 (див. JavaDoc класу).
     */
    @Коли("перший оператор зберігає свою зміну")
    public void перший_оператор_зберігає() {
        String body = updateBody(List.of("UA", "PL"));
        HttpResponse<String> response = E2eStack.putConfig(body, version);
        assertThat(response.statusCode())
            .as("перший PUT з If-Match %d мав пройти; тіло: %s", version, response.body())
            .isEqualTo(200);
    }

    /**
     * Другий оператор із ТІЄЮ САМОЮ (уже застарілою) версією: PUT іншого
     * тіла — відповідь зберігається в поле без асерта (статус перевіряє
     * наступний крок; цей крок — лише дія).
     */
    @Коли("другий оператор зберігає іншу зміну з тією самою версією")
    public void другий_оператор_зберігає_з_тією_самою_версією() {
        String body = updateBody(List.of("UA", "DE"));
        secondResponse = E2eStack.putConfig(body, version);
    }

    /**
     * Відповідь другого оператора: статус 409, {@code Content-Type:
     * application/problem+json}, у тілі ProblemDetail поле {@code status} == 409
     * (формат помилок — контракт, не самописний JSON).
     */
    @Тоді("запит другого оператора відхилено з конфліктом")
    public void запит_другого_відхилено_з_конфліктом() {
        assertThat(secondResponse.statusCode())
            .as("Другий PUT зі stale-версією %d має отримати 409; тіло %s", version, secondResponse.body())
            .isEqualTo(409);
        assertThat(secondResponse.headers().firstValue("Content-Type").orElse(""))
            .as("Другий PUT зі stale-версією %d в заголовку має бути Content-Type: application/problem+json", version)
            .startsWith("application/problem+json");

        JsonNode response = E2eStack.json(secondResponse.body());
        assertThat(response.get("status").asInt())
            .as("Другий PUT зі stale-версією %d в тілі також має повертати 409", version)
            .isEqualTo(409);
    }

    /**
     * Lost update НЕ стався: {@code getConfig()} → у values правила r1 —
     * зміна ПЕРШОГО оператора (і жодного сліду зміни другого), а
     * {@code version} == прочитана на початку + 1 (рівно один успішний запис).
     */
    @Тоді("збереженою лишається зміна першого оператора")
    public void збереженою_лишається_зміна_першого() {
        JsonNode response = E2eStack.json(E2eStack.getConfig().body());

        Set<String> countries = response.findParents("attribute").stream()
            .filter(node -> "country".equals(node.path("attribute").asText()))
            .map(node -> node.get("values"))
            .flatMap(valueNode -> valueNode.isArray()
                ? StreamSupport.stream(valueNode.spliterator(), false).map(JsonNode::asText)
                : Stream.of(valueNode.asText())
            )
            .collect(Collectors.toSet());

        assertThat(countries)
            .as("Другий PUT зі stale-версією %d не повинен переписувати значення", version)
            .containsExactlyInAnyOrder("UA", "PL");
        assertThat(response.get("version").asLong())
            .as("Другий PUT зі stale-версією %d, версія має зрости рівно на 1 при першому PUT", version)
            .isEqualTo(version + 1);
    }

    private String updateBody(List<String> countries) {

        JsonMapper jsonMapper = new JsonMapper();
        JsonNode updatedRules = configNode.path("rules").deepCopy();

        StreamSupport.stream(updatedRules.spliterator(), false)
            .filter(record -> "r1".equals(record.path("id").asText()))
            .flatMap(record -> StreamSupport.stream(record.path("clauses").spliterator(), false))
            .filter(clause -> "country".equals(clause.path("attribute").asText()))
            .findFirst()
            .ifPresent(clause -> ((ObjectNode) clause).set("values", jsonMapper.valueToTree(countries)));

        ObjectNode request = jsonMapper.createObjectNode();
        request.set("enabled", configNode.get("enabled"));
        request.set("defaultVariant", configNode.get("defaultVariant"));
        request.set("offVariant", configNode.get("offVariant"));
        request.set("rules", updatedRules);
        request.set("rollout", configNode.get("rollout"));

        try {
            return jsonMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
