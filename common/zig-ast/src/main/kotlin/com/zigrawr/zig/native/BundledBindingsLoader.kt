package com.zigrawr.zig.native

import java.nio.file.Paths

object BundledBindingsLoader {

    fun load(): Handles {
        val osStr = when (SupportedOs.current) {
            SupportedOs.Linux -> "linux"
            SupportedOs.Mac -> "macos"
            SupportedOs.Windows -> "windows"
            null -> TODO("Handle unsupported os gracefully")
        }
        val archStr = when (SupportedArch.current) {
            SupportedArch.x64 -> "x86_64"
            SupportedArch.arm64 -> "aarch64"
            null -> TODO("Handle unsupported arch gracefully")
        }

        val binaryPath = BundledBindingsLoader::class.java.getResource("/native/zigLib/${archStr}-${osStr}")
            ?: TODO("Handle missing binary gracefully")
        return Loader.loadFromPath(Paths.get(binaryPath.toURI()))
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
