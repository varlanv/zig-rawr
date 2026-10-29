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
tasks.processResources {
    val zigLib = internalConvention.zigLib.get()
    dependsOn(zigLib.aggregateTaskName)

    zigLib.targetBinaryPaths.get().forEach { (target, path) ->
        from(path) {
            into("native/zigLib")
            rename { target.toZigTarget() }
        }
    }
}
