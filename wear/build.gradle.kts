// Watch app for the session (V1.4a, design.md 17): remote and display only, the phone decides.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Same applicationId and signing key as the phone app, or the Data Layer does not pair them.
    namespace = "com.avlreminder.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.avlreminder"
        // As on the phone (anforderungen.md 8.1): costs nothing, test watches from Wear OS 5.
        minSdk = 34
        targetSdk = 36
        versionCode = 35
        versionName = "1.4a"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }

    // As for :app, but without a baseline: the module starts without findings.
    lint {
        warningsAsErrors = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    // The permission request (ActivityResult) needs a Fragment from 1.3; wear-ongoing brings 1.1 along.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.wear.compose:compose-material3:1.7.2")
    implementation("androidx.wear.compose:compose-foundation:1.7.2")
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-material3:1.4.2")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation("androidx.wear.tiles:tiles-testing:1.6.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
