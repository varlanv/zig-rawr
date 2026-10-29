import java.util.Properties

plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    implementation(projects.common.commonLang)
}


internalConvention {
    zigLib {
        enabled = true
        targets(com.varlanv.gradle.plugin.InternalConventionExtension.ZigLib.RequestedTarget.AllKnownSupported)
    }
}

val writeNativeProperties = tasks.register("writeNativeProperties",WriteProperties::class) {
    description = "Write metadata properties to bundle"
    destinationFile.set(
        layout.buildDirectory.file("generated/native/metadata")
    )
    val zigLib = internalConvention.zigLib.get()
    property("library.name", zigLib.libraryName)
    property("library.version", project.version.toString())
    property("library.mode", providers.gradleProperty("com.zigrawr.zig.runMode").getOrElse("dev"))
}


tasks.processResources {
    val zigLib = internalConvention.zigLib.get()
    val pathPrefix = "native/zigLib"
    dependsOn(zigLib.aggregateTaskName)

    zigLib.targetBinaryPaths.get().forEach { (target, path) ->
        from(path) {
            into("$pathPrefix/${target.toZigTarget()}")
        }
    }
    from(writeNativeProperties) {
        into("native/zigLib")
    }
}
