plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jib)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jackson)

    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    implementation(libs.spring.boot.starter.kafka)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> { useJUnitPlatform() }

// Дзеркало конфігурації control-plane — деталі у steps/04a-kind-helm-jib.md (I1)
jib {
    from {
        image = "registry.access.redhat.com/ubi9/openjdk-25-runtime:latest"
        // платформа = архітектура машини збірки (пояснення — control-plane/build.gradle.kts)
        platforms {
            platform {
                os = "linux"
                architecture = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "amd64"
            }
        }
    }
    to { image = "ghcr.io/koroljovpavel/praporets-analytics:local" }
    container {
        ports = listOf("8082")
    }
    // явний docker-шлях для демона з IDE-PATH (пояснення — control-plane)
    File("/usr/local/bin/docker").takeIf { it.exists() }
        ?.let { dockerClient { executable = it.absolutePath } }
}
