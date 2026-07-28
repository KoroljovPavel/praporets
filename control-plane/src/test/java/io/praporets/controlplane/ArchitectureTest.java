package io.praporets.controlplane;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

/**
 * Архітектурні guardrails (G10). Ці правила зелені з першого дня — їхня
 * цінність у тому, що вони ЗАЛИШАТЬСЯ зеленими: порушення шарів або field
 * injection упаде на CI, а не в код-рев'ю.
 */
@AnalyzeClasses(packages = "io.praporets", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Жорстке правило проекту: ядро — ZERO фреймворків, лише JDK. */
    @ArchTest
    static final ArchRule core_depends_only_on_jdk =
            classes().that().resideInAPackage("io.praporets.core..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("io.praporets.core..", "java..");

    /** Ін'єкція — тільки через конструктор. */
    @ArchTest
    static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /** Domain — нижній шар: не знає ні про api, ні про service. */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_upper_layers =
            noClasses().that().resideInAPackage("..controlplane.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..controlplane.api..", "..controlplane.service..");

    /**
     * Сервіси не залежать від веб-шару (контролери, error handler).
     * DTO ({@code api.dto}) — дозволені: це спільний контракт, який сервіси
     * повертають, тому правило б'є лише по пакету {@code api} без підпакетів.
     */
    @ArchTest
    static final ArchRule services_do_not_depend_on_web_layer =
            noClasses().that().resideInAPackage("..controlplane.service..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("io.praporets.controlplane.api");

    /** Контролери ходять у БД лише через сервіси — репозиторії їм заборонені. */
    @ArchTest
    static final ArchRule controllers_do_not_touch_repositories =
            noClasses().that().resideInAPackage("..controlplane.api..")
                    .should().dependOnClassesThat().areAssignableTo(Repository.class);
}
