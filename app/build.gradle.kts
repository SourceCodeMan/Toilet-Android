plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.tomchapman.flushsimulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tomchapman.flushsimulator"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            // material-icons-extended ships a couple of thousand vectors and this app
            // uses twenty-six. R8 is what makes that difference disappear.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric needs the merged resources to stand up an Android runtime in a
    // plain JVM test.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// The app compiles to 17 for Android, but Robolectric refuses to stand up an SDK 36
// sandbox on anything below 21. Only the test JVM needs to be newer.
tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // Twenty-six glyphs the app needs and Compose does not ship by default. The
    // alternative was hand-drawing every one as a vector path.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)

    // The drawing is the whole deliverable here and no assertion can judge it, so it
    // is rendered to PNGs on the JVM instead. Roborazzi drives Compose through
    // Robolectric's native graphics — no emulator, no device.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
