import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

repositories {
    mavenCentral()
    // JetBrains dependencies repository
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    implementation("org.jetbrains.jediterm:jediterm-typeahead:2.69")

    // JetBrains JediTerm Terminal Emulator
    implementation("org.jetbrains.jediterm:jediterm-pty:2.69")

    // JetBrains Native PTY Wrapper
    implementation("org.jetbrains.pty4j:pty4j:0.13.12")



    // IntelliJ Platform Gradle Plugin Dependencies Extension
    intellijPlatform {
        clion("2026.2")
        testFramework(TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}