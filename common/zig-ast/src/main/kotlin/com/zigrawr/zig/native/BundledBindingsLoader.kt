package com.zigrawr.zig.native

import com.zigrawr.lang.log.Log
import java.nio.file.Paths

object BundledBindingsLoader {

    context(log: Log)
    fun load(): Handles {
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

        val binaryPathStr = "/native/zigLib/${archStr}-${osStr}"
        val binaryUrl = BundledBindingsLoader::class.java.getResource(binaryPathStr)
        if (binaryUrl == null) {
            log.debug { "Binary is missing at path [ $binaryPathStr ]" }
            TODO("Handle missing binary gracefully")
        }
        return try {
            Loader.loadFromPath(Paths.get(binaryUrl.toURI()))
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
