package com.zigrawr.jbplugin.run

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.jetbrains.cidr.execution.debugger.CidrDebuggerLanguageSupportManager
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointType
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ZigDebugSpec : IdeaHarness({
    should("launch the registered native debugger and suspend at a Zig line breakpoint") {
        runDebugFixture { fixture ->
            val directory = Files.createTempDirectory("zigrawr-debug-smoke-")
            val path = directory.resolve("debug-main.zig")
            Files.writeString(path, """
                const std = @import("std");
                pub fn main() void {
                    const answer: u32 = 42;
                    std.debug.print("{d}\n", .{answer});
                }
            """.trimIndent())
            val source = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
            fixture.configureFromExistingVirtualFile(source)
            PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            val settings = RunManager.getInstance(fixture.project)
                .createConfiguration("Zig debugger smoke", ZigRunConfigurationType.getInstance().factory)
            val configuration = settings.configuration as ZigRunConfiguration
            configuration.kind = ZigRunKind.MAIN
            configuration.filePath = source.path
            val executor = DefaultDebugExecutor.getDebugExecutorInstance()
            val runner = ProgramRunner.getRunner(executor.id, configuration)!!
            runner.runnerId shouldBe ZigDebugRunner.ID
            val debugger = XDebuggerManager.getInstance(fixture.project)
            val breakpointType = XBreakpointType.EXTENSION_POINT_NAME.extensionList
                .filterIsInstance<CidrLineBreakpointType>().first()
            val line = 3
            breakpointType.canPutAt(source, line, fixture.project) shouldBe true
            ReadAction.computeBlocking<String, RuntimeException> {
                service<CidrDebuggerLanguageSupportManager>().createEditor(fixture.project, configuration)
                    .createDocument(fixture.project, XExpressionImpl.fromText("answer"), XSourcePositionImpl.create(source, line), EvaluationMode.EXPRESSION).text
            } shouldBe "answer"
            val breakpoint = ApplicationManager.getApplication().runWriteAction(Computable {
                debugger.breakpointManager.addLineBreakpoint(breakpointType, source.url, line, breakpointType.createProperties())
            })
            try {
                runner.execute(ExecutionEnvironmentBuilder.create(executor, settings).build())
                PlatformTestUtil.waitWithEventsDispatching(
                    "The native debugger did not suspend at the Zig breakpoint.",
                    { debugger.debugSessions.any { it.isSuspended } }, 30,
                )
                val position = debugger.debugSessions.single { it.isSuspended }.currentPosition!!
                position.file shouldBe source
                position.line shouldBe line
            } finally {
                debugger.debugSessions.forEach { it.stop() }
                PlatformTestUtil.waitWithEventsDispatching("The native debugger did not stop.", { debugger.debugSessions.isEmpty() }, 10)
                WriteAction.run<RuntimeException> { debugger.breakpointManager.removeBreakpoint(breakpoint) }
                FileUtil.delete(directory.toFile())
            }
        }
    }
})

private fun runDebugFixture(block: (CodeInsightTestFixture) -> Unit) {
    runInEdtAndWait {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createFixtureBuilder("zig native debugging", true)
        builder.addModule(EmptyModuleFixtureBuilder::class.java)
        val fixture = factory.createCodeInsightFixture(builder.fixture)
        fixture.setUp()
        try {
            block(fixture)
        } finally {
            fixture.tearDown()
        }
    }
}
