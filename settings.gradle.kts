pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.3"
}

stonecutter {
    create(rootProject) {
        versions("26.3", "26.2", "1.21.11", "1.21.1")
        // Le code de src/ est ecrit pour cette version
        vcsVersion = "26.3"
    }
}

rootProject.name = "xaeroheadtracker"
