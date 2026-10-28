package com.varlanv.gradle.plugin

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency
import kotlin.jvm.optionals.getOrNull

class InternalCatalog(private val versionCatalogsExtension: VersionCatalogsExtension) {

    companion object {
        const val NAME = "__internal_convention_properties__"
    }

    private val versionCatalog = versionCatalogsExtension.named("libs")

    /**
     * Get lib from "libraries" block in libs.versions.toml
     */
    fun getLib(name: String): Provider<MinimalExternalModuleDependency> {
        return versionCatalog
            .findLibrary(name)
            .getOrNull() ?: error("Unable to find library [$name]")
    }

    /**
     * Get lib from "plugins" block in libs.versions.toml
     */
    fun getPlugin(name: String): Provider<PluginDependency> {
        return versionCatalog
            .findPlugin(name)
            .orElse(null) ?: error("Unable to find plugin [$name]")
    }

    /**
     * Get lib from "versions" block in libs.versions.toml
     */
    fun getVersion(name: String): String {
        return versionCatalog.findVersion(name).getOrNull()?.requiredVersion
            ?: error("Unable to find version [$name]")
    }
}
