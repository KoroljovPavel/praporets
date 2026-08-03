plugins { id("java") }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

// Step definitions використовують локалізовані анотації (io.cucumber.java.uk.Дано
// тощо) — кодування джерел фіксуємо явно, щоб компіляція не залежала від локалі
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(platform(libs.cucumber.bom))
    // Версії testcontainers/jackson/postgres беруться з ТОГО Ж quarkus-BOM, що
    // вже керує ними у flag-edge — жодної нової версії поза каталогом
    testImplementation(platform(libs.quarkus.bom))

    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.assertj)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.postgresql)
}

// e2e ганяється ТІЛЬКИ явним запитом (`make test-e2e` /
// `./gradlew :e2e:test`) — повний `./gradlew build` лишається швидким,
// e2e у CI — на main/nightly
val e2eExplicitlyRequested = gradle.startParameter.taskNames.any { it.contains("e2e") }

tasks.test {
    useJUnitPlatform()
    onlyIf("e2e запускається тільки явним ':e2e:test' або 'make test-e2e'") {
        e2eExplicitlyRequested
    }

    if (e2eExplicitlyRequested) {
        dependsOn(":control-plane:bootJar", ":flag-edge:quarkusBuild")
    }
    systemProperty(
        "praporets.cp.jar",
        rootProject.layout.projectDirectory
            .file("control-plane/build/libs/control-plane-${version}.jar").asFile.absolutePath
    )
    systemProperty(
        "praporets.edge.app",
        rootProject.layout.projectDirectory
            .dir("flag-edge/build/quarkus-app").asFile.absolutePath
    )

    // стек контейнерів живе весь прогін; сценарії шарять його послідовно
    maxParallelForks = 1
}
