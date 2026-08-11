package io.praporets.controlplane.service;

import io.praporets.controlplane.AbstractIntegrationTest;
import io.praporets.controlplane.api.dto.*;
import io.praporets.controlplane.domain.*;
import io.praporets.core.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.StringNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Транзакційна семантика ревізій та аудиту на рівні сервісів —
 * те, чого не видно крізь HTTP: монотонність ревізій між різними типами змін,
 * канонічна форма payload, before/after в аудиті, доменна валідація.
 */
class RevisionAndAuditFlowTest extends AbstractIntegrationTest {

    @Autowired
    EnvironmentService environments;
    @Autowired
    FlagService flags;
    @Autowired
    SegmentService segments;
    @Autowired
    FlagConfigService configs;
    @Autowired
    EnvironmentRepository environmentRepository;
    @Autowired
    RevisionLogRepository revisionLog;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    EntityManager em;

    private static final String ACTOR = "alice";

    private void createEnvironment() {
        environments.create(new CreateEnvironmentRequest("dev", "Development"), ACTOR);
    }

    private FlagResponse createBooleanFlag() {
        return flags.create(new CreateFlagRequest(
                "checkout.new-flow", "New checkout", null, ValueType.BOOLEAN,
                List.of(new VariantDto("on", BooleanNode.TRUE), new VariantDto("off", BooleanNode.FALSE))), ACTOR);
    }

    private UpsertFlagConfigRequest config(List<Rule> rules) {
        return new UpsertFlagConfigRequest(true, "on", "off", rules, null);
    }

    /** Скидає persistence context, щоб асерти читали з БД, а не з кешу першого рівня. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void config_change_creates_revision_with_canonical_payload() {
        createEnvironment();
        createBooleanFlag();

        var rule = new Rule("r1",
                List.of(new Clause("country", Operator.IN, List.of("UA"), false)), "on", null);
        var result = configs.upsert("dev", "checkout.new-flow", null, config(List.of(rule)), ACTOR);
        assertThat(result.created()).isTrue();
        flushAndClear();

        assertThat(environmentRepository.findByKey("dev").orElseThrow().getRevision()).isEqualTo(1);

        List<RevisionLogEntry> entries = revisionLog.findByEnvironmentKeyOrderByRevisionDesc("dev", Limit.of(10));
        assertThat(entries).hasSize(1);
        var entry = entries.getFirst();
        assertThat(entry.getRevision()).isEqualTo(1);
        assertThat(entry.getChangeType()).isEqualTo(ChangeType.FLAG_CONFIG_UPDATED);
        // канонічна JSON-форма: імена компонентів core-records
        assertThat(entry.getPayload().at("/rules/0/variantKey").asText()).isEqualTo("on");
        assertThat(entry.getPayload().get("defaultVariant").asText()).isEqualTo("on");
    }

    @Test
    void revisions_are_monotonic_across_change_kinds() {
        createEnvironment();
        createBooleanFlag();

        configs.upsert("dev", "checkout.new-flow", null, config(List.of()), ACTOR);
        segments.upsert("dev", "beta-testers", new UpsertSegmentRequest(
                List.of(new Clause("plan", Operator.IN, List.of("pro"), false))), ACTOR);
        configs.toggle("dev", "checkout.new-flow", false, ACTOR);
        flushAndClear();

        assertThat(environmentRepository.findByKey("dev").orElseThrow().getRevision()).isEqualTo(3);
        assertThat(revisionLog.findByEnvironmentKeyOrderByRevisionDesc("dev", Limit.of(10)))
                .extracting(RevisionLogEntry::getRevision, RevisionLogEntry::getChangeType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3L, ChangeType.FLAG_TOGGLED),
                        org.assertj.core.groups.Tuple.tuple(2L, ChangeType.SEGMENT_UPDATED),
                        org.assertj.core.groups.Tuple.tuple(1L, ChangeType.FLAG_CONFIG_UPDATED));
    }

    @Test
    void audit_captures_actor_before_and_after() {
        var created = createBooleanFlag();
        flags.update("checkout.new-flow", 0, new UpdateFlagRequest("Renamed", null), "bob");
        flushAndClear();

        var entries = auditLog.findByEntityIdOrderByIdDesc(created.id(), Limit.of(10));
        assertThat(entries).hasSize(2);

        var update = entries.getFirst();
        assertThat(update.getAction()).isEqualTo("UPDATE");
        assertThat(update.getActor()).isEqualTo("bob");
        assertThat(update.getBefore().get("name").asText()).isEqualTo("New checkout");
        assertThat(update.getAfter().get("name").asText()).isEqualTo("Renamed");

        var create = entries.getLast();
        assertThat(create.getAction()).isEqualTo("CREATE");
        assertThat(create.getActor()).isEqualTo(ACTOR);
        assertThat(create.getBefore()).isNull();
        assertThat(create.getAfter().get("key").asText()).isEqualTo("checkout.new-flow");
    }

    @Test
    void config_update_enforces_if_match_semantics() {
        createEnvironment();
        createBooleanFlag();
        configs.upsert("dev", "checkout.new-flow", null, config(List.of()), ACTOR);

        // оновлення без If-Match → 400-помилка домену
        assertThatThrownBy(() -> configs.upsert("dev", "checkout.new-flow", null, config(List.of()), ACTOR))
                .isInstanceOf(DomainValidationException.class);

        // застаріла версія → 409
        assertThatThrownBy(() -> configs.upsert("dev", "checkout.new-flow", 5L, config(List.of()), ACTOR))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void variant_value_must_match_flag_value_type() {
        assertThatThrownBy(() -> flags.create(new CreateFlagRequest(
                "pricing.model", "Pricing", null, ValueType.BOOLEAN,
                List.of(new VariantDto("on", StringNode.valueOf("yes")))), ACTOR))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("on");
    }

    @Test
    void rule_and_rollout_variant_references_must_exist() {
        createEnvironment();
        createBooleanFlag();
        var clause = new Clause("country", Operator.IN, List.of("UA"), false);

        // правило вказує на неіснуючий варіант
        var ghostRule = new Rule("r1", List.of(clause), "ghost", null);
        assertThatThrownBy(() -> configs.upsert("dev", "checkout.new-flow", null,
                config(List.of(ghostRule)), ACTOR))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("ghost");

        // бакет rollout-а вказує на неіснуючий варіант
        var ghostRollout = new Rollout("salt-1", List.of(new Bucket("ghost", 100_000)));
        assertThatThrownBy(() -> configs.upsert("dev", "checkout.new-flow", null,
                new UpsertFlagConfigRequest(true, "on", "off", List.of(), ghostRollout), ACTOR))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void default_variant_must_exist_among_flag_variants() {
        createEnvironment();
        createBooleanFlag();

        assertThatThrownBy(() -> configs.upsert("dev", "checkout.new-flow", null,
                new UpsertFlagConfigRequest(true, "ghost", "off", List.of(), null), ACTOR))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("ghost");
    }
}
