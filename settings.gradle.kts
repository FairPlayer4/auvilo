pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.4.1"
        id("org.jetbrains.kotlin.android") version "2.4.21"
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
        id("com.google.devtools.ksp") version "2.3.12"
        id("org.jetbrains.kotlinx.kover") version "0.9.11"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "avlreminder"
include(":core", ":app")

// Cloud-Backup (docs/backup-entwurf.md A-2): Die Module protokoll und verbindung
// kommen als Quellcode aus dem Backend-Repo daneben (Composite Build), nicht als
// veröffentlichte Bibliothek. Lokal baut Gradle, was dort gerade liegt; die CI
// checkt genau den Commit aus gradle/backend-commit.txt aus.
val backendOrdner = file("../avlreminder-backend")
includeBuild(backendOrdner.resolve("client"))

// Weicht das Backend vom festgehaltenen Commit ab oder hat es uncommittete
// Änderungen in client/ oder proto/, baut die App gegen einen anderen Stand als
// die CI. Das ist beim Entwickeln gewollt, eine solche APK kommt aber nicht aufs
// Handy. Mit -PbackendStreng=true bricht der Build stattdessen ab.
run {
    val festgehalten = file("gradle/backend-commit.txt").readText().trim()
    fun git(vararg args: String): String =
        providers.exec {
            commandLine("git", "-C", backendOrdner.path, *args)
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    val ist = git("rev-parse", "HEAD")
    val geaendert = git("status", "--porcelain", "--", "client", "proto")
    val abweichung = when {
        ist != festgehalten -> "Backend steht auf ${ist.take(12)}, festgehalten ist ${festgehalten.take(12)} (gradle/backend-commit.txt)"
        geaendert.isNotEmpty() -> "Backend hat uncommittete Änderungen in client/ oder proto/"
        else -> null
    }
    if (abweichung != null) {
        if (providers.gradleProperty("backendStreng").orNull == "true") throw GradleException(abweichung)
        // quiet statt warn: Der übliche Build läuft mit -q und zeigte eine Warnung nicht.
        logger.quiet("WARNUNG: $abweichung - diese APK nicht installieren.")
    }
}
