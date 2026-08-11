package io.praporets.core.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Вхідні дані обчислення: хто користувач і що ми про нього знаємо.
 *
 * <p>Приклад: {@code userKey="user-42", attributes={country=UA, plan=pro, appVersion=5.2.1}}.
 *
 * <p><b>Інваріанти:</b> {@code userKey} не blank; {@code attributes} — незмінна
 * захисна копія (без {@code null} ключів/значень; порожня мапа легальна).
 *
 * @param userKey    стабільний ідентифікатор користувача — основа бакетування
 * @param attributes довільні атрибути для таргетингу
 */
public record EvaluationContext(String userKey, Map<String, String> attributes) {

    /**
     * Ім'я псевдо-атрибута, через який clause може таргетуватися на конкретних
     * користувачів: {@code userKey IN [user-42, user-43]}.
     */
    public static final String USER_KEY_ATTRIBUTE = "userKey";

    /**
     * @throws NullPointerException     якщо {@code userKey}, {@code attributes},
     *                                  ключ або значення мапи {@code null}
     * @throws IllegalArgumentException якщо {@code userKey} blank
     */
    public EvaluationContext {
        attributes = Map.copyOf(attributes);

        Objects.requireNonNull(userKey, "userKey");

        if (userKey.isBlank()) throw new IllegalArgumentException("userKey must be non-blank");
    }

    /**
     * Значення атрибута для матчингу.
     *
     * <p>Ім'я {@value #USER_KEY_ATTRIBUTE} <b>завжди</b> резолвиться
     * в {@link #userKey()} — навіть якщо в {@code attributes} лежить ключ із такою
     * самою назвою (ідентичність користувача не можна перекрити атрибутом).
     *
     * @param name ім'я атрибута (не {@code null})
     * @return значення або {@link Optional#empty()}, якщо атрибут відсутній
     * @throws NullPointerException якщо {@code name} {@code null}
     */
    public Optional<String> attribute(String name) {
        Objects.requireNonNull(name, "name");

        if (name.equals(USER_KEY_ATTRIBUTE)) return Optional.of(userKey);

        return Optional.ofNullable(attributes.get(name));
    }
}
