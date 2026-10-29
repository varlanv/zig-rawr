package com.varlanv.gradle.plugin

import com.varlanv.gradle.plugin.InternalConventionExtension.ZigLib
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Exec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class InternalConventionPlugin : Plugin<Project> {

    private class Impl(private val project: Project) {
        private val rootProject = project.rootProject
        private val objects = project.objects
        private val extensions = project.extensions
        private val providers = project.providers
        private val pluginManager = project.pluginManager
        private val tasks = project.tasks
        private val repositories = project.repositories
        private val dependencies = project.dependencies
        private val internalEnvironment = extensions.findByName(InternalEnvironment.NAME) as InternalEnvironment?
            ?: InternalEnvironment(providers.environmentVariable("CI").isPresent, false)
        private val internalCatalog = extensions.findByName(InternalCatalog.NAME) as InternalCatalog?
            ?: InternalCatalog(extensions.getByName("versionCatalogs") as VersionCatalogsExtension)
        private val internalConventionExtension =
            extensions.findByName(InternalConventionExtension.NAME) as InternalConventionExtension?
                ?: extensions.create(InternalConventionExtension.NAME, InternalConventionExtension::class.java)
        private val jvmVersion = internalCatalog.getVersion("jvmVersion")
        private val kotlinVersion = internalCatalog.getVersion("kotlinVersion")
        private val jvmVendor = JvmVendorSpec.ADOPTIUM
        private val zigDir = rootProject.file("native").resolve("zig")
        private val zigLib get() = internalConventionExtension.zigLib.get()

        fun run() {
            val targets = objects.setProperty(ZigLib.RequestedTarget::class.java)
                .convention(setOf(ZigLib.RequestedTarget.CurrentMachine))
            internalConventionExtension.zigLib.set(
                ZigLib(
                    enabled = objects.property(Boolean::class.java).convention(false),
                    currentMachineBinaryPath = objects.property(String::class.java).let {
                        val target = ZigLib.ResolvedTarget.currentMachine
                        if (target == null) {
                            it
                        } else {
                            it.convention(zigTargetLibBinaryPath(target))
                        }
                    },
                    requestedTargets = targets,
                    targetBinaryPaths = targets.map { targets ->
                        val res = mutableMapOf<ZigLib.ResolvedTarget, String>()
                        for (requestedTarget in targets) {
                            when (requestedTarget) {
                                ZigLib.RequestedTarget.CurrentMachine -> {
                                    val target = ZigLib.ResolvedTarget.currentMachine ?: throw GradleException(
                                        unsupportedTargetErrorMessage()
                                    )
                                    res[target] = zigTargetLibBinaryPath(target)
                                }

                                ZigLib.RequestedTarget.AllKnownSupported -> {
                                    for (target in ZigLib.ResolvedTarget.allSupported) {
                                        res[target] = zigTargetLibBinaryPath(target)
                                    }
                                }
                            }
                        }
                        res
                    }
                )
            )

            configureRepositories()
            pluginManager.apply(internalCatalog.getPlugin("kotlin-jvm").get().pluginId)
            pluginManager.apply("maven-publish")
            pluginManager.withPlugin("org.jetbrains.intellij.platform") {
                repositories.intellijPlatform {
                    it.defaultRepositories()
                }

                (dependencies as ExtensionAware).extensions.configure(
                    IntelliJPlatformDependenciesExtension::class.java
                ) {
                    it.clion(internalCatalog.getVersion("clionVersion"))
                }
            }
            project.afterEvaluate {
                applyCommonPlugins()
                configureKotlin()
                configureCommonDependencies()
                configureTests()
                configurePublishing()
                configureZigLibBinaryCurrentMachineTask()
                configureZigLibBinaryAggregateTask()
            }
        }

        fun applyCommonPlugins() {
            if (internalEnvironment.isLocal()) {
                pluginManager.apply(IdeaPlugin::class.java)
                extensions.configure<IdeaModel>("idea") {
                    it.module.isDownloadJavadoc = true
                    it.module.isDownloadSources = true
                }
            }
            tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
                kotlinCompile.compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
//                    allWarningsAsErrors.set(true)
                    extraWarnings.set(true)
                    progressiveMode.set(true)
                    freeCompilerArgs.addAll("-Xjsr305=strict")
                }
            }
        }

        fun configureCommonDependencies() {
            dependencies.add(
                "testImplementation",
                dependencies.create("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            )
            if (project.name != "common-test") {
                dependencies.add(
                    "testImplementation",
                    dependencies.project(mapOf("path" to ":testing:common-test"))
                )
            }
        }

        fun configureRepositories() {
            if (internalEnvironment.isLocal()) {
                repositories.add(repositories.mavenLocal())
            }
            repositories.add(repositories.mavenCentral())
        }

        fun configureKotlin() {
            tasks.withType(org.gradle.api.tasks.compile.JavaCompile::class.java) { javaCompile ->
                javaCompile.options.release.set(jvmVersion.toInt())
            }
            extensions.configure<KotlinJvmProjectExtension>("kotlin") { kotlin ->
                kotlin.jvmToolchain { jvmToolchain ->
                    jvmToolchain.languageVersion.set(JavaLanguageVersion.of(jvmVersion))
                    jvmToolchain.vendor.set(jvmVendor)
                }
            }
        }

        fun configureTests() {
            tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach { test ->
                test.useJUnitPlatform()
                test.testLogging { logging ->
                    logging.showStandardStreams = true
                    logging.showStackTraces = true
                }
            }
        }

        fun configurePublishing() {
            extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") { publishing ->
                if (publishing.publications.findByName("maven") == null) {
                    publishing.publications.create(
                        "maven",
                        org.gradle.api.publish.maven.MavenPublication::class.java,
                    ) {
                        it.from(project.components.getByName("java"))
                    }
                }
            }
        }

        fun configureZigLibBinaryCurrentMachineTask() {
            tasks.register(zigLib.currentMachineTaskName, Exec::class.java) {
                val target =
                    ZigLib.ResolvedTarget.currentMachine ?: throw GradleException(unsupportedTargetErrorMessage())
                val zigTarget = target.toZigTarget()
                it.description = "Task for building binary for current machine - $zigTarget"
                it.commandLine(zigBuildCmdForTarget(target))
                it.workingDir = zigDir
                it.outputs.file(zigTargetLibBinaryPath(target))
                it.inputs.dir(zigDir.resolve("src"))
                it.inputs.file(zigDir.resolve("build.zig"))
            }
        }

        fun configureZigLibBinaryAggregateTask() {
            val zigLibBinariesAggregateTask = tasks.register(zigLib.aggregateTaskName) {
                it.description = "Aggregate task for building Zig library binaries for requested targets"
            }
            val perTargetTasks =
                internalConventionExtension.zigLib.get().targetBinaryPaths.get().map { (target, binaryPath) ->
                    tasks.register(
                        "zigLibBinary_${target.os.name.lowercase()}_${target.architecture.name.lowercase()}",
                        Exec::class.java
                    ) {
                        it.description = "Task for building binary for ${target.toZigTarget()}"
                        it.commandLine(zigBuildCmdForTarget(target))
                        it.workingDir = zigDir
                        it.outputs.file(binaryPath)
                        it.inputs.dir(zigDir.resolve("src"))
                        it.inputs.file(zigDir.resolve("build.zig"))
                    }
                }
            zigLibBinariesAggregateTask.configure {
                it.dependsOn(perTargetTasks)
            }
        }

        fun zigBuildCmdForTarget(target: ZigLib.ResolvedTarget): List<String> {
            val targetStr = target.toZigTarget()
            return listOf(
                "zig",
                "build",
                "-Dtarget=${targetStr}",
                "-Dstrip=true",
                "-Doptimize=fast",
                "-DlibName=${ZIG_LIB_NAME}",
                "-p",
                "zig-out/${targetStr}"
            )
        }

        fun zigTargetLibBinaryPath(target: ZigLib.ResolvedTarget): String {
            return zigDir.resolve("zig-out").resolve(target.toZigTarget())
                .resolve(if (target.os == ZigLib.SupportedOs.Windows) "bin" else "lib")
                .resolve(target.toZigLibBinaryName()).absolutePath
        }

        private fun unsupportedTargetErrorMessage(): String = "Current machine os/architecture is not supported; see " +
            "[com.varlanv.gradle.plugin.InternalConventionExtension.ZigLib.SupportedOs], " +
            "[com.varlanv.gradle.plugin.InternalConventionExtension.ZigLib.SupportedArch]"
    }

    override fun apply(target: Project) {
        Impl(target).run()
    }
}
