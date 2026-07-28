plugins { id ("java-library") }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    testImplementation(platform(libs.junit.bom))

    testImplementation(libs.junit.jupiter)

    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.assertj)
    testImplementation(libs.jqwik)
}

tasks.test {
    useJUnitPlatform()
}
