import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: all rules and use cases, no Android. Coverage is enforced here.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlinx.kover")
    // Fakes and the day simulation, shared with the app's tests (real data, RealDataTest).
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        verify {
            rule("Domain logic coverage") {
                minBound(90)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
}
