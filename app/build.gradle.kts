/*
 * This file was generated by the Gradle 'init' task.
 *
 * The key point is a minimum solution for PSI parser.
 * Currently, the build is a kotlin CLI
 * This generated file contains a sample Kotlin application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.10.1/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
    kotlin("jvm") version "2.1.0"
    id("com.adarshr.test-logger") version "4.0.0"
}

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("compiler-embeddable"))

    testImplementation(kotlin("test"))
}

// Apply a specific Java toolchain to ease working on different environments.
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

application {
    // Define the main class for the application.
    mainClass = "AppKt"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
