plugins {
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.intellij.platform)
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        clion(libs.versions.clionVersion)
    }
}
