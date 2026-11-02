import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.intellij.platform)
}

dependencies {
    implementation(projects.common.commonLang)
    implementation(projects.common.zigAst)

    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("idea.load.plugins.id", "com.varlanv.zigrawr,intellij.libraries.misc.plugin")
}
