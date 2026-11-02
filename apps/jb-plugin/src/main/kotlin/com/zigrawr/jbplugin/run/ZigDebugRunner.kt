package com.zigrawr.jbplugin.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.createDriverConfiguration
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.nio.file.Files
import java.nio.file.Path

class ZigDebugRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is ZigRunConfiguration

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        FileDocumentManager.getInstance().saveAllDocuments()
        val configuration = environment.runProfile as ZigRunConfiguration
        val result = AsyncPromise<RunContentDescriptor?>()
        object : Task.Backgroundable(environment.project, "Building Zig debug executable", true) {
            private var directory: Path? = null
            private lateinit var command: GeneralCommandLine
            private lateinit var driver: DebuggerDriverConfiguration

            override fun run(indicator: ProgressIndicator) {
                configuration.checkConfiguration()
                val toolchain = CPPToolchains.getInstance().defaultToolchain
                    ?: throw ExecutionException("Configure a local CLion toolchain and debugger before debugging Zig.")
                if (toolchain.toolSetKind.isRemoteLike) {
                    throw ExecutionException("Zig debugging currently requires a local CLion toolchain.")
                }
                val temporary = Files.createTempDirectory("zigrawr-debug-")
                directory = temporary
                val executable = temporary.resolve(if (SystemInfo.isWindows) "program.exe" else "program")
                val compile = ZigRunCommandLineState.createCommandLine(configuration, buildOnly = true, output = executable)
                compile.addParameter("-fno-strip")
                indicator.text = "Compiling ${Path.of(configuration.filePath).fileName}"
                val output = CapturingProcessHandler(compile).runProcessWithProgressIndicator(indicator)
                indicator.checkCanceled()
                if (output.isCancelled) throw ProcessCanceledException()
                if (output.exitCode != 0) {
                    val diagnostics = output.stderr.ifBlank { output.stdout }.takeLast(16 * 1024)
                    throw ExecutionException("Zig debug compilation failed (exit ${output.exitCode}).\n$diagnostics")
                }
                if (!Files.isRegularFile(executable)) {
                    throw ExecutionException("Zig did not produce the debug executable: $executable")
                }
                command = GeneralCommandLine(executable.toString()).withCharset(Charsets.UTF_8)
                    .withParentEnvironmentType(compile.parentEnvironmentType).withEnvironment(compile.environment)
                compile.workDirectory?.let(command::withWorkDirectory)
                if (configuration.kind == ZigRunKind.MAIN) {
                    command.addParameters(ParametersList.parse(configuration.programArguments).toList())
                }
                val nativeEnvironment = CPPEnvironment(toolchain, environment.project, compile.workDirectory)
                driver = createDriverConfiguration(environment.project, nativeEnvironment)
                if (driver.hostMachine.isRemote || driver.hostMachine.isWsl) {
                    throw ExecutionException("Zig debugging currently requires a local CLion debugger.")
                }
            }

            override fun onSuccess() {
                if (environment.project.isDisposed) {
                    cleanup()
                    result.cancel()
                    return
                }
                try {
                    val starter = object : XDebugProcessStarter() {
                        override fun start(session: XDebugSession): XDebugProcess = CidrLocalDebugProcess(
                            TrivialRunParameters(driver, command), session,
                            TextConsoleBuilderFactory.getInstance().createBuilder(environment.project),
                        ).also { it.start() }
                    }
                    val started = XDebuggerManager.getInstance(environment.project).newSessionBuilder(starter)
                        .environment(environment).startSession()
                    started.session.addSessionListener(object : XDebugSessionListener {
                        override fun sessionStopped() = cleanup()
                    })
                    (started.session.debugProcess as CidrLocalDebugProcess).afterDebugSessionStarted()
                    result.setResult(started.runContentDescriptor)
                } catch (error: Throwable) {
                    cleanup()
                    result.setError(error)
                }
            }

            override fun onCancel() {
                cleanup()
                result.cancel()
            }

            override fun onThrowable(error: Throwable) {
                cleanup()
                result.setError(error)
            }

            private fun cleanup() {
                val temporary = directory ?: return
                directory = null
                AppExecutorUtil.getAppExecutorService().execute { FileUtil.delete(temporary.toFile()) }
            }
        }.queue()
        return result
    }

    companion object {
        const val ID = "ZigRawrDebugRunner"
    }
}
