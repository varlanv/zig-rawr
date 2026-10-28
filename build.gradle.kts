plugins {
    idea
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kover) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.intellij.platform) apply false
    alias(libs.plugins.versionCatalogPlugin)
}

val isLocal = !providers.environmentVariable("CI").isPresent

if (isLocal) {
    idea {
        module {
            excludeDirs.add(rootDir.resolve(".intellijPlatform"))
        }
    }
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
