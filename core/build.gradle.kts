plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The rules of the game, and nothing else.
//
// Deliberately a plain JVM module rather than an Android one: no SDK, no emulator,
// no Robolectric, so the whole of the flush — the timeline, the grading, the upkeep
// loop, the save format and the engine — is covered by tests that run in under a
// second on any machine. Storage, sound and haptics reach it as interfaces
// (Platform.kt), and the Android module supplies the real ones.
dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    // Matches what the Android module will compile against, so the same bytecode
    // level is in play on both sides of the fence.
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
    }
}
