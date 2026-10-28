plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kover) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.versionCatalogPlugin)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

versionCatalogUpdate {
    keep {
        keepUnusedVersions = true
    }
}
