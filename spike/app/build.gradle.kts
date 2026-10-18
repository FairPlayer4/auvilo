plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.avlreminder.spike"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.avlreminder.spike"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-spike"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("com.google.android.gms:play-services-location:21.4.0")
}
