// Replays fingerprint recordings through the app's positioning engine, offline: where the app
// would have placed the phone each second, how far that was from where it stood, and what each
// anchor heard. Run it with `make replay ARGS="..."` (see the Makefile).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

application {
    mainClass.set("br.org.cesar.blueguide.tools.replay.MainKt")
    applicationName = "replay"
}

dependencies {
    implementation(project(":core:dataset"))
    implementation(project(":core:positioning"))
    testImplementation(libs.junit)
}
