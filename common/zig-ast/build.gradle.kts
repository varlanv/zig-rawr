plugins {
    alias(libs.plugins.internalConvention)
}

internalConvention {
    zigAstDependency = true
}

dependencies {
    implementation(projects.common.commonLang)
}
