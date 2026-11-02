package com.zigrawr.jbplugin.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.executeState
import com.intellij.openapi.components.service
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import java.nio.file.Path
import org.jetbrains.concurrency.resolvedPromise

class ZigRunCommandLineState(environment: ExecutionEnvironment, private val configuration: ZigRunConfiguration) : CommandLineState(environment) {
    init {
        addConsoleFilters(RegexpFilter(configuration.project, "\$FILE_PATH\$:\$LINE\$:\$COLUMN\$:.*"))
    }

    override fun startProcess(): ProcessHandler {
        val handler = KillableColoredProcessHandler(createCommandLine(configuration))
        handler.setShouldDestroyProcessRecursively(true)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    companion object {
        fun createCommandLine(configuration: ZigRunConfiguration, buildOnly: Boolean = false, output: Path? = null): GeneralCommandLine {
            configuration.checkConfiguration()
            val toolchain = configuration.project.service<ZigToolchainService>().currentInfo
                ?: throw ExecutionException("Select and check a Zig toolchain in Settings | Languages & Frameworks | Zig.")
            val sourcePath = Path.of(configuration.filePath)
            val projectPath = Path.of(configuration.project.basePath ?: ".")
            val source = (if (sourcePath.isAbsolute) sourcePath else projectPath.resolve(sourcePath)).toAbsolutePath().normalize()
            val workingDirectory = if (configuration.workingDirectory.isBlank()) source.parent else {
                val path = Path.of(configuration.workingDirectory)
                if (path.isAbsolute) path else projectPath.resolve(path)
            }
            val command = GeneralCommandLine(toolchain.executable).withCharset(Charsets.UTF_8).withWorkingDirectory(workingDirectory)
            command.addParameter(if (configuration.kind == ZigRunKind.MAIN) if (buildOnly) "build-exe" else "run" else "test")
            command.addParameter(source.toString())
            command.addParameters(ParametersList.parse(configuration.compilerArguments).toList())
            if (buildOnly) {
                val binary = output ?: throw ExecutionException("A debug executable output path is required.")
                command.addParameters("-femit-bin=${binary.toAbsolutePath()}")
                if (configuration.kind != ZigRunKind.MAIN) command.addParameter("--test-no-exec")
            }
            if (configuration.kind == ZigRunKind.TEST || configuration.kind == ZigRunKind.TEST_GROUP) {
                val filter = if (configuration.kind == ZigRunKind.TEST_GROUP) configuration.testName.trimEnd('.') + "."
                    else configuration.testName
                command.addParameters("--test-filter", filter)
            }
            if (!buildOnly && configuration.kind == ZigRunKind.MAIN && configuration.programArguments.isNotBlank()) {
                command.addParameter("--")
                command.addParameters(ParametersList.parse(configuration.programArguments).toList())
            }
            return command
        }
    }
}

class ZigProgramRunner : ProgramRunner<RunnerSettings> {
    override fun getRunnerId(): String = "ZigRawrRun"
    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is ZigRunConfiguration

    override fun execute(environment: ExecutionEnvironment) {
        val state = environment.state ?: return
        ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
            resolvedPromise(executeState(state, environment, this))
        }
    }
}
