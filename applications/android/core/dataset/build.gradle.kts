// The fingerprint recordings' format: the CSV rows and the metadata JSON. The app writes them and
// reads them back to upload; the offline replay reads them too.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // ContractSamples, the values the expected files were written from, for the app's API
    // contract test as well as this module's own.
    `java-test-fixtures`
}

// No Android here, by construction (see :core:model).
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // FingerprintRecord holds DeviceId and the sensor samples.
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
