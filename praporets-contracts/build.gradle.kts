plugins {
    id("java-library")
    alias(libs.plugins.protobuf)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    // api, не implementation (камінь #3): згенеровані класи світять цими
    // типами в публічних сигнатурах — споживачі мають отримати їх транзитивно
    api(libs.protobuf.java)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)

    testImplementation(project(":praporets-core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    // бінарник protoc тієї самої версії, що й runtime protobuf-java —
    // розсинхрон «генерував новішим, ніж бібліотека» ловиться ще на компіляції
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" }
    }
    generateProtoTasks {
        all().forEach { task -> task.plugins { create("grpc") } }
    }
}

tasks.test { useJUnitPlatform() }
