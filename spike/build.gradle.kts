// AGP 9 has built-in Kotlin support. The Kotlin plugin is only on the classpath
// so AGP and the Compose compiler use the same Kotlin version.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
