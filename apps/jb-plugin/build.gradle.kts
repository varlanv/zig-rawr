import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.intellij.platform)
}

dependencies {
    implementation(projects.common.commonLang)
    implementation(projects.common.zigAst)

    intellijPlatform {
        bundledPlugin("com.intellij.nativeDebug")
        bundledPlugin("com.intellij.clion")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit4)
}

tasks.test {
    systemProperty("idea.load.plugins.id", "com.varlanv.zigrawr,intellij.libraries.misc.plugin")
}
