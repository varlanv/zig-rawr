package com.varlanv.gradle.plugin

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

const val ZIG_LIB_NAME = "zigRawrNative"

interface InternalConventionExtension {

    companion object {
        const val NAME = "internalConvention"
    }


    val zigLib: Property<ZigLib>

    fun zigLib(block: Action<ZigLib>) {
        block.execute(zigLib.get())
    }

    class ZigLib(
        val enabled: Property<Boolean>,
        val aggregateTaskName: String = "zigLibBinariesAggregate",
        val currentMachineTaskName: String = "zigLibBinary_currentMachine",
        val currentMachineBinaryPath: Property<String>,
        internal val requestedTargets: SetProperty<RequestedTarget>,
        val targetBinaryPaths: Provider<Map<ResolvedTarget, String>>,
    ) {

        fun targets(vararg targets: RequestedTarget) {
            val set = targets.toSet()
            if (targets.isEmpty()) {
                error("At least one target should be provided, for example: `com.varlanv.gradle.plugin.InternalConventionExtension.ZigLib.Target.CurrentMachine`")
            }
            if (set.size != targets.size) {
                error("Targets should be unique: ${targets.toList()}")
            }
            this.requestedTargets.set(set)
        }

        enum class RequestedTarget {
            CurrentMachine,
            AllKnownSupported,
        }

        data class ResolvedTarget(val os: SupportedOs, val architecture: SupportedArch) {

            fun toZigLibBinaryName(): String {
                return when (os) {
                    SupportedOs.Linux -> "lib${ZIG_LIB_NAME}.so"
                    SupportedOs.Mac -> "lib${ZIG_LIB_NAME}.dylib"
                    SupportedOs.Windows -> "${ZIG_LIB_NAME}.dll"
                }
            }

            fun toZigTarget(): String {
                return when (architecture) {
                    SupportedArch.x64 -> when (os) {
                        SupportedOs.Linux -> "x86_64-linux"
                        SupportedOs.Mac -> "x86_64-macos"
                        SupportedOs.Windows -> "x86_64-windows"
                    }

                    SupportedArch.arm64 -> when (os) {
                        SupportedOs.Linux -> "aarch64-linux"
                        SupportedOs.Mac -> "aarch64-macos"
                        SupportedOs.Windows -> "aarch64-windows"
                    }
                }
            }

            companion object {

                val currentMachine: ResolvedTarget? =
                    if (SupportedOs.current != null && SupportedArch.current != null) {
                        ResolvedTarget(SupportedOs.current, SupportedArch.current)
                    } else null
                val allSupported: Set<ResolvedTarget> = SupportedOs.entries.flatMapTo(HashSet()) { os ->
                    SupportedArch.entries.map { arch -> ResolvedTarget(os, arch) }
                }
            }
        }

        enum class SupportedOs {
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

        enum class SupportedArch {
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
}
