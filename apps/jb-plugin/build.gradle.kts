plugins {
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.intellij.platform)
}

dependencies {
    implementation(projects.common.commonLang)
    implementation(projects.common.zigAst)
}
