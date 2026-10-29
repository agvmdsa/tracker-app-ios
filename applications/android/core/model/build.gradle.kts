// The app's plain data (anchors, points, device ids, sightings), shared by every layer.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// No Android here, by construction: nothing in this module can reach the framework. Java 11
// bytecode, the same as the app's.
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
    testImplementation(libs.junit)
}
