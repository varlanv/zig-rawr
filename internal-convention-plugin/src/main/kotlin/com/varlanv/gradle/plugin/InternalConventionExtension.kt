package com.varlanv.gradle.plugin

import org.gradle.api.provider.Property

interface InternalConventionExtension {

    companion object {
        const val NAME = "internalConvention"
    }

    val zigAstDependency: Property<Boolean>
    val zigAstBinaryPath: Property<String>
}
