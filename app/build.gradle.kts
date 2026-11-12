plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.avlreminder"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.avlreminder"
        minSdk = 37
        targetSdk = 37
        versionCode = 17
        versionName = "1.3b1-backup5"
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
            // Robolectric reaches into JDK internals for SDK 37 (FileDescriptor in ApplicationSharedMemory).
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }

    // Android Lint (Berechtigungen, API-Level, Compose, Manifest). Was beim Einführen schon da
    // war, steht in lint-baseline.xml; jeder neue Fund, auch eine Warnung, macht den Build rot.
    // Die Baseline schrumpft, wenn Funde behoben werden (lintDebug meldet dann "fixed").
    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        // Diese Funde entstehen ohne Codeänderung, sobald neue Versionen erscheinen; Updates
        // macht Kevin bewusst, sie sollen die CI nicht von allein rot machen.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    // Cloud-Backup: protokoll und verbindung aus ../avlreminder-backend/client
    // (Composite Build, settings.gradle.kts; docs/cloud-entscheidungen.md A-2).
    implementation("com.avlreminder.client:verbindung")
    // "Mit Google anmelden" for the cloud backup (A-4).
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    // Hourly cloud backup (cloud-entscheidungen.md A-9); Glance brings an older version along.
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Geofence and current position (V1.1b, tried in the spike)
    implementation("com.google.android.gms:play-services-location:21.4.0")

    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")

    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    // Compose tests drive Espresso; older versions call InputManager.getInstance(), which SDK 37 no longer has.
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.work:work-testing:2.12.0")
    testImplementation(testFixtures(project(":core")))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
