package com.zigrawr.jbplugin.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.jetbrains.cidr.execution.CidrDebugProfile
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.Icon

enum class ZigRunKind(private val label: String) {
    MAIN("Main function"),
    TEST("Single test"),
    TEST_GROUP("Test group"),
    TEST_FILE("All tests in file");

    override fun toString(): String = label
}

class ZigRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var targetKind by string(ZigRunKind.MAIN.name)
    var filePath by string("")
    var testName by string("")
    var compilerArguments by string("")
    var programArguments by string("")
    var workingDirectory by string("")
}

class ZigRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : LocatableConfigurationBase<ZigRunConfigurationOptions>(project, factory, name), CidrDebugProfile {
    override fun getOptions(): ZigRunConfigurationOptions = super.getOptions() as ZigRunConfigurationOptions

    override fun getOptionsClass(): Class<ZigRunConfigurationOptions> = ZigRunConfigurationOptions::class.java

    var kind: ZigRunKind
        get() = ZigRunKind.entries.firstOrNull { it.name == options.targetKind } ?: ZigRunKind.MAIN
        set(value) {
            options.targetKind = value.name
        }

    var filePath: String
        get() = options.filePath.orEmpty()
        set(value) {
            options.filePath = value
        }

    var testName: String
        get() = options.testName.orEmpty()
        set(value) {
            options.testName = value
        }

    var compilerArguments: String
        get() = options.compilerArguments.orEmpty()
        set(value) {
            options.compilerArguments = value
        }

    var programArguments: String
        get() = options.programArguments.orEmpty()
        set(value) {
            options.programArguments = value
        }

    var workingDirectory: String
        get() = options.workingDirectory.orEmpty()
        set(value) {
            options.workingDirectory = value
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = ZigRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        ZigRunCommandLineState(environment, this)

    override fun checkConfiguration() {
        if (filePath.isBlank()) throw RuntimeConfigurationError("Select a Zig source file.")
        try {
            val source = resolvePath(filePath)
            if (!filePath.endsWith(".zig") || !Files.isRegularFile(source) || !Files.isReadable(source)) {
                throw RuntimeConfigurationError("Zig source file is not readable: $filePath")
            }
            if (workingDirectory.isNotBlank() && !Files.isDirectory(resolvePath(workingDirectory))) {
                throw RuntimeConfigurationError("Working directory does not exist: $workingDirectory")
            }
        } catch (_: InvalidPathException) {
            throw RuntimeConfigurationError("A source file or working directory path is invalid.")
        }
        if (kind == ZigRunKind.TEST || kind == ZigRunKind.TEST_GROUP) {
            if (testName.isBlank()) throw RuntimeConfigurationError("Enter a test name or test group.")
            ReadAction.computeBlocking<Unit, RuntimeConfigurationError> {
                val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvePath(filePath))
                    ?: throw RuntimeConfigurationError("Zig source file is unavailable: $filePath")
                val file = PsiManager.getInstance(project).findFile(virtualFile)
                    ?: throw RuntimeConfigurationError("Zig source file is unavailable: $filePath")
                ZigRunTargets.checkTestSelection(file, kind, testName)
            }
        }
    }

    private fun resolvePath(value: String): Path {
        val path = Path.of(value)
        return if (path.isAbsolute) path else Path.of(project.basePath ?: ".").resolve(path)
    }
}

class ZigRunConfigurationType : ConfigurationType, DumbAware {
    val factory: ConfigurationFactory = object : ConfigurationFactory(this) {
        override fun getId(): String = "Zig"
        override fun getOptionsClass(): Class<ZigRunConfigurationOptions> = ZigRunConfigurationOptions::class.java
        override fun isEditableInDumbMode(): Boolean = true
        override fun createTemplateConfiguration(project: Project): RunConfiguration =
            ZigRunConfiguration(project, this, "Zig")
    }

    override fun getDisplayName(): String = "Zig"
    override fun getConfigurationTypeDescription(): String = "Run a Zig program or tests"
    override fun getIcon(): Icon = AllIcons.RunConfigurations.Application
    override fun getId(): String = ID
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)

    companion object {
        const val ID = "ZigRawrRun"

        fun getInstance(): ZigRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(ZigRunConfigurationType::class.java)
    }
}
