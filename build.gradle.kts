// AGP 9 has built-in Kotlin support. The Kotlin Android plugin is only on the classpath
// so AGP and the Compose compiler use the same Kotlin version.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("com.google.devtools.ksp") apply false
    id("org.jetbrains.kotlinx.kover") apply false
}
