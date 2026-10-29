// Where a phone is, from what it hears: the path loss model, the RSSI windows and the solver.
// The same code places the phone in the app and in an offline replay of a recording.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
    // Anchor, Point2D and DeviceId are part of this module's API.
    api(project(":core:model"))
    testImplementation(libs.junit)
}
