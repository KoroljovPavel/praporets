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
    from { image = "registry.access.redhat.com/ubi9/openjdk-25-runtime:latest" }
    to { image = "ghcr.io/koroljovpavel/praporets-control-plane:local" }
    container {
        ports = listOf("8080", "9090") // REST + gRPC (метадані, не публікація)
    }
}
