plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    api(libs.kotlin.kotest.assertionsCore)
    api(libs.kotlin.kotest.frameworkEngine)
    api(libs.kotlin.kotest.junit5Runner)
    api(libs.kotlin.kotest.assertionsJvm)
}
