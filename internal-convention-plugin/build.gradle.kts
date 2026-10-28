plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

val isCiBuild = providers.environmentVariable("CI").orNull != null

kotlin {
    jvmToolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmVersion.get()))
    }
}

if (!isCiBuild) {
    pluginManager.apply(IdeaPlugin::class.java)
    val idea = extensions.getByName("idea") as org.gradle.plugins.ide.idea.model.IdeaModel
    idea.module.isDownloadJavadoc = true
    idea.module.isDownloadSources = true
}

repositories {
    if (!isCiBuild) {
        mavenLocal()
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.main)
}

gradlePlugin {
    plugins {
        create("internalGradleConventionPlugin") {
            id = libs.plugins.internalConvention.get().pluginId
            implementationClass = "com.varlanv.gradle.plugin.InternalConventionPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("wrasse.realRepoCatalogPath", file("../gradle/libs.versions.toml").absolutePath)
    systemProperty("wrasse.realRepoRoot", file("..").absolutePath)
}
