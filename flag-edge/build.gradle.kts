plugins {
    java
    alias(libs.plugins.quarkus)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.hibernate.validator)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.micrometer.registry.prometheus)

    implementation(project(":praporets-contracts"))
    implementation(project(":praporets-core"))

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":control-plane:bootJar")
    systemProperty(
        "praporets.cp.jar",
        rootProject.layout.projectDirectory
            .file("control-plane/build/libs/control-plane-${version}.jar").asFile.absolutePath
    )
}
