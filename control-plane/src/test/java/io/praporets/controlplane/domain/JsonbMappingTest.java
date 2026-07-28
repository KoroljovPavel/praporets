package io.praporets.controlplane.domain;

import io.praporets.core.model.Bucket;
import io.praporets.core.model.Clause;
import io.praporets.core.model.Operator;
import io.praporets.core.model.Rollout;
import io.praporets.core.model.Rule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1: JSONB-колонки зберігають records ядра і повертають їх еквівалентними.
 * Еквівалентність records — це рівність значень, тож roundtrip-тести ловлять
 * будь-яку втрату даних у серіалізації.
 */
class JsonbMappingTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    private static final List<Clause> CONDITIONS = List.of(
            new Clause("country", Operator.IN, List.of("UA", "PL"), false),
            new Clause("appVersion", Operator.SEMVER_GREATER_OR_EQUAL, List.of("5.2"), true));

    @Test
    void segment_conditions_survive_roundtrip() {
        var environment = em.persist(new Environment("prod", "Production"));
        var segment = em.persistFlushFind(new Segment(environment, "beta", CONDITIONS));
        em.clear();

        var reloaded = em.find(Segment.class, segment.getId());

        assertThat(reloaded.getConditions()).isEqualTo(CONDITIONS);
    }

    @Test
    void flag_config_rules_and_rollout_survive_roundtrip() {
        var environment = em.persist(new Environment("prod", "Production"));
        var flag = em.persist(new Flag("checkout.new-flow", "New flow", null, ValueType.BOOLEAN));
        var rollout = new Rollout("salt-1", List.of(
                new Bucket("on", 30_000), new Bucket("off", 70_000)));
        var rules = List.of(
                new Rule("r-ua", CONDITIONS, "on", null),
                new Rule("r-roll", List.of(), null, rollout));

        var config = new FlagConfig(flag, environment, "off", "off");
        config.setEnabled(true);
        config.setRules(rules);
        config.setRollout(rollout);
        var saved = em.persistFlushFind(config);
        em.clear();

        var reloaded = em.find(FlagConfig.class, saved.getId());

        assertThat(reloaded.getRules()).isEqualTo(rules);
        assertThat(reloaded.getRollout()).isEqualTo(rollout);
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void jsonb_columns_are_real_jsonb_not_text() {
        var environment = em.persist(new Environment("prod", "Production"));
        em.persistAndFlush(new Segment(environment, "beta", CONDITIONS));

        // jsonb_typeof працює тільки на справжньому jsonb — на varchar/text цей запит впаде
        String conditionsType = jdbc.queryForObject(
                "select jsonb_typeof(conditions) from segment", String.class);

        assertThat(conditionsType).isEqualTo("array");
    }

    @Test
    void variant_value_is_real_jsonb() {
        var flag = new Flag("ui.banner", "Banner", null, ValueType.JSON);
        flag.addVariant(new Variant("on", "{\"color\":\"blue\",\"limit\":10}"));
        em.persistAndFlush(flag);

        String valueType = jdbc.queryForObject(
                "select jsonb_typeof(value) from variant", String.class);

        assertThat(valueType).isEqualTo("object");
    }
}
