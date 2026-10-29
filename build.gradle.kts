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
            val zigDir = rootDir.resolve("native").resolve("zig")
            isDownloadJavadoc = true
            isDownloadSources = true
            excludeDirs.add(rootDir.resolve(".intellijPlatform"))
            excludeDirs.add(zigDir.resolve("zig-out"))
            excludeDirs.add(zigDir.resolve(".zig-cache"))
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
