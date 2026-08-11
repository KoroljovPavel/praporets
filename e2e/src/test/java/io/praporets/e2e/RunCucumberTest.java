package io.praporets.e2e;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Точка входу Cucumber через JUnit Platform Suite — Gradle бачить цей
 * клас як звичайний тест, cucumber-junit-platform-engine знаходить
 * {@code .feature}-файли на класпасі поруч ({@code io/praporets/e2e}) і
 * матчить кроки на glue-пакет {@code io.praporets.e2e}.
 *
 * <p>Рівно 3 сценарії в 3 feature-файлах — свідоме скоуп-рішення:
 * {@code propagation.feature}, {@code cp-outage.feature},
 * {@code concurrent-edits.feature}. Нових не додавати — усе інше
 * покривають інтеграційні тести модулів.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("io/praporets/e2e")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "io.praporets.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class RunCucumberTest {
}
