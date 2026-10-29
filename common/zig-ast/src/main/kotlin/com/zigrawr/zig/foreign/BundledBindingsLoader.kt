package com.zigrawr.zig.foreign

import com.zigrawr.lang.log.Log
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.*

object BundledBindingsLoader {

    context(log: Log)
    fun load(): Handles {
        val pathPrefix = "/native/zigLib"
        val metadataPath = "$pathPrefix/metadata"
        val metadataIs = BundledBindingsLoader::class.java.getResourceAsStream(metadataPath)
        if (metadataIs == null) {
            log.debug { "Missing metadata file at path [ $metadataPath ]" }
            TODO("Handle error")
        }
        val properties = Properties()
        metadataIs.use {
            properties.load(it)
        }
        val libraryName = properties["library.name"]?.toString()
        if (libraryName == null) {
            log.debug { "library.name property is missing" }
            TODO("Handle error")
        }
        log.debug { "libraryName - [$libraryName]" }

        val osStr = when (SupportedOs.current) {
            SupportedOs.Linux -> "linux"
            SupportedOs.Mac -> "macos"
            SupportedOs.Windows -> "windows"
            null -> {
                log.debug { "Resolved unsupported OS - ${System.getProperty("os.name")}" }
                TODO("Handle unsupported os gracefully")
            }
        }
        log.debug { "Resolved platform OS [ $osStr ]" }
        val archStr = when (SupportedArch.current) {
            SupportedArch.x64 -> "x86_64"
            SupportedArch.arm64 -> "aarch64"
            null -> {
                log.debug { "Resolved unsupported architecture - ${System.getProperty("os.arch")}" }
                TODO("Handle unsupported arch gracefully")
            }
        }
        log.debug { "Resolved architecture [ $archStr ]" }

        val binaryName = System.mapLibraryName(libraryName)
        val binaryPathStr = "${pathPrefix}/${archStr}-${osStr}/${binaryName}"
        val binaryUrl = BundledBindingsLoader::class.java.getResource(binaryPathStr)
        if (binaryUrl == null) {
            log.debug { "Binary is missing at path [ $binaryPathStr ]" }
            TODO("Handle missing binary gracefully")
        }
        return try {
            val binaryPath = if (binaryUrl.protocol == "file") {
                Paths.get(binaryUrl.toURI())
            } else {
                // need to extract the file in case it's inside the JAR so that OS can load it
                val extracted = Files.createTempFile("zig-rawr", null)
                extracted.toFile().deleteOnExit()
                binaryUrl.openStream().use { Files.copy(it, extracted, REPLACE_EXISTING) }
                extracted
            }
            Loader.loadFromPath(binaryPath)
        } catch (e: Exception) {
            log.debug { "Failed to load binary - [ ${e.message} ]" }
            throw e
        }
    }

    private enum class SupportedOs {
        Linux,
        Mac,
        Windows;

        companion object {

            val current: SupportedOs? = System.getProperty("os.name").lowercase().let { osName ->
                when {
                    osName.startsWith("windows") -> Windows
                    osName.startsWith("mac") || osName.startsWith("darwin") -> Mac
                    osName.startsWith("linux") -> Linux
                    else -> null
                }
            }
        }
    }

    private enum class SupportedArch {
        x64,
        arm64;

        companion object {

            val current: SupportedArch? = System.getProperty("os.arch").lowercase().let { os ->
                when (os) {
                    "amd64", "x86_64" -> x64
                    "aarch64", "arm64" -> arm64
                    else -> null
                }
            }
        }
    }
}
