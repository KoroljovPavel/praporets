plugins {
    java
    alias(libs.plugins.quarkus)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.micrometer.registry.prometheus)

    implementation(project(":praporets-contracts"))
    implementation(project(":praporets-core"))

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj)
}

tasks.test { useJUnitPlatform() }
