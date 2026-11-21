import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Server des Cloud-Backups (Anf. 7.34 OV1): Adresse und Web-Client-ID stehen nicht im Repo,
// damit fremde Builds nicht Kevins Server treffen. Gelesen als Gradle-Property (-P, die
// gradle.properties in GRADLE_USER_HOME) oder aus local.properties. Ohne beide Werte blendet
// die App das Cloud-Backup aus; eine solche APK kommt nicht auf ein Handy mit Cloud-Backup.
val lokaleWerte = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
    .map { text -> Properties().apply { load(text.reader()) } }
fun cloudWert(name: String): String =
    (providers.gradleProperty(name).orNull ?: lokaleWerte.orNull?.getProperty(name)).orEmpty().trim()
val cloudServerUrl = cloudWert("avlCloudServerUrl")
val cloudWebClientId = cloudWert("avlCloudWebClientId")
when {
    cloudServerUrl.isEmpty() && cloudWebClientId.isEmpty() ->
        // quiet statt warn: Der übliche Build läuft mit -q und zeigte eine Warnung nicht.
        logger.quiet("WARNUNG: avlCloudServerUrl und avlCloudWebClientId fehlen - Cloud-Backup ausgeblendet, " +
            "diese APK nicht auf ein Handy mit Cloud-Backup installieren.")
    cloudServerUrl.isEmpty() || cloudWebClientId.isEmpty() ->
        throw GradleException("avlCloudServerUrl und avlCloudWebClientId nur zusammen setzen")
    !cloudServerUrl.startsWith("https://") ->
        throw GradleException("avlCloudServerUrl muss mit https:// beginnen")
    !cloudWebClientId.endsWith(".apps.googleusercontent.com") ->
        throw GradleException("avlCloudWebClientId ist keine OAuth-Client-ID (*.apps.googleusercontent.com)")
}

android {
    namespace = "com.avlreminder"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.avlreminder"
        minSdk = 34
        targetSdk = 37
        versionCode = 36
        versionName = "1.4a1"
        buildConfigField("String", "CLOUD_SERVER_URL", "\"$cloudServerUrl\"")
        buildConfigField("String", "CLOUD_WEB_CLIENT_ID", "\"$cloudWebClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    // Watch app (V1.4a, design.md 17): messages to the watch nearby, no data items.
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

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
