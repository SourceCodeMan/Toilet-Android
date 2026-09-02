pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Fetches a matching JDK when the machine does not already have the one the
    // build asks for, so a fresh checkout builds without anyone installing
    // anything first.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "flush-simulator"

// `core` is the rules: pure Kotlin, no Android, no Compose, so it runs in a plain
// JVM test. `app` is the drawing, which needs the Android SDK.
include(":core")
include(":app")
