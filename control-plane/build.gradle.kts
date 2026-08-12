plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jib)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(project(":praporets-core"))
    // gRPC-сервер ConfigService; contracts дає згенеровані класи
    // і (транзитивно, через api) protobuf-java + grpc-stub
    implementation(project(":praporets-contracts"))
    implementation(libs.spring.boot.starter.grpc.server)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jackson)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.actuator)
    // 04c: registry вмикає /actuator/prometheus (його скрейпить ServiceMonitor).
    // runtimeOnly, як в analytics: код компілюється проти micrometer-core
    // (транзитивно з actuator), формат Prometheus — деталь рантайму. Версією
    // керує Boot BOM.
    runtimeOnly(libs.micrometer.registry.prometheus)
    // transactional outbox → Kafka (стартер, не голий spring-kafka:
    // Boot 4 модульний — ConnectionDetails/автоконфіг живуть у spring-boot-kafka)
    implementation(libs.spring.boot.starter.kafka)
    // JsonFormat для proto→JSON у payload outbox; contracts дає лише core protobuf-java
    implementation(libs.protobuf.java.util)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    // клієнтські стаби + in-process транспорт для gRPC-тестів
    testImplementation(libs.spring.boot.starter.grpc.client)
    testImplementation(libs.spring.boot.starter.grpc.client.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> { useJUnitPlatform() }

// Reproducible-образ без Dockerfile. `jibDockerBuild` кладе
// образ у локальний Docker daemon (звідти його забирає `kind load`);
// push у ghcr.io — справа CI, не локальної збірки. База — UBI runtime:
// distroless для Java 25 не існує, аргументи в steps/04a-kind-helm-jib.md (I1)
jib {
    from {
        image = "registry.access.redhat.com/ubi9/openjdk-25-runtime:latest"
        // Дефолт Jib — linux/amd64 НЕЗАЛЕЖНО від хоста: на Apple Silicon такий
        // образ у kind їде через Rosetta-емуляцію (тихо і повільно), а
        // native-edge взагалі падає. Платформа = архітектура машини збірки;
        // у CI (amd64) вираз дає linux/amd64 сам собою
        platforms {
            platform {
                os = "linux"
                architecture = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "amd64"
            }
        }
    }
    to { image = "ghcr.io/koroljovpavel/praporets-control-plane:local" }
    container {
        ports = listOf("8080", "9090") // REST + gRPC (метадані, не публікація)
    }
    // Gradle-демон, породжений IDE з GUI, не має /usr/local/bin у PATH і Jib
    // не знаходить docker; явний шлях (тільки якщо існує — CI не чіпаємо)
    File("/usr/local/bin/docker").takeIf { it.exists() }
        ?.let { dockerClient { executable = it.absolutePath } }
}
