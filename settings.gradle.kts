pluginManagement {
    plugins {
        kotlin("jvm") version "2.4.10"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "machikado-java"
