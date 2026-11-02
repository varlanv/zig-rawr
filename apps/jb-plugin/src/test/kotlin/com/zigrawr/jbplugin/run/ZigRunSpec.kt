package com.zigrawr.jbplugin.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.file.ZigLanguage
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.jbplugin.toolchain.ZigToolchainSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.util.concurrent.CompletableFuture

class ZigRunSpec : IdeaHarness({
    should("produce and execute a main configuration from its registered gutter target") {
        runSelectionFixture({ fixture ->
            fixture.project.service<ZigToolchainSettings>().executable = ""
            PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            val source = """
                const std = @import("std");
                const Nested = struct { fn main() void {} };
                fn helper() void {}
                pub fn main() void { std.debug.print("RUN_SMOKE\n", .{}); }
            """.trimIndent()
            val file = fixture.addFileToProject("space dir/run smoke.zig", source)
            fixture.configureFromExistingVirtualFile(file.virtualFile)
            val main = source.indexOf("pub fn main") + "pub fn ".length
            hasMarker(fixture, main) shouldBe true
            hasMarker(fixture, source.indexOf("fn helper") + 3) shouldBe false
            hasMarker(fixture, source.indexOf("fn main") + 3) shouldBe false
            val configuration = configuration(fixture, main, ZigRunKind.MAIN)
            configuration.filePath shouldBe file.virtualFile.path
            val output = execute(configuration)
            output.exitCode shouldBe 0
            (output.stdout + output.stderr) shouldContain "RUN_SMOKE"
        }, "zig main execution")
    }

    should("reject ambiguous test filters and execute a renamed test, a namespace group, and all file tests") {
        runSelectionFixture({ fixture ->
            fixture.project.service<ZigToolchainSettings>().executable = ""
            PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            val source = """
                const std = @import("std");
                const Group = struct {
                    test "one" { std.debug.print("GROUP_ONE\n", .{}); }
                    test "two" { std.debug.print("GROUP_TWO\n", .{}); }
                };
                const GroupOther = struct {
                    test "forbidden" { std.debug.print("UNWANTED_GROUP\n", .{}); return error.WrongGroup; }
                };
                comptime { _ = Group; _ = GroupOther; }
                test "foo" { std.debug.print("SINGLE_SELECTED\n", .{}); }
                test "foobar" { std.debug.print("UNWANTED_SINGLE\n", .{}); return error.MustNotRun; }
            """.trimIndent()
            val file = fixture.addFileToProject("checks.zig", source)
            fixture.configureFromExistingVirtualFile(file.virtualFile)
            val singleOffset = source.indexOf("test \"foo\"")
            hasMarker(fixture, singleOffset) shouldBe true
            val single = configuration(fixture, singleOffset, ZigRunKind.TEST)
            single.testName shouldBe "checks.test.foo"
            for (buildOnly in listOf(false, true)) {
                val error = shouldThrow<RuntimeConfigurationError> {
                    ZigRunCommandLineState.createCommandLine(single, buildOnly = buildOnly)
                }
                error.localizedMessage.orEmpty() shouldContain "substring"
                error.localizedMessage.orEmpty() shouldContain "checks.test.foo"
                error.localizedMessage.orEmpty() shouldContain "checks.test.foobar"
                error.localizedMessage.orEmpty() shouldContain "Rename"
            }
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                fixture.editor.document.setText(source.replace("test \"foobar\"", "test \"barfoo\""))
                PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
            }
            FileDocumentManager.getInstance().saveDocument(fixture.editor.document)
            val singleOutput = execute(single)
            singleOutput.exitCode shouldBe 0
            (singleOutput.stdout + singleOutput.stderr) shouldContain "SINGLE_SELECTED"
            (singleOutput.stdout + singleOutput.stderr) shouldNotContain "UNWANTED_SINGLE"
            val group = configuration(fixture, source.indexOf("const Group") + 6, ZigRunKind.TEST_GROUP)
            group.testName shouldBe "checks.Group"
            val groupOutput = execute(group)
            groupOutput.exitCode shouldBe 0
            val groupText = groupOutput.stdout + groupOutput.stderr
            groupText shouldContain "GROUP_ONE"
            groupText shouldContain "GROUP_TWO"
            groupText shouldNotContain "UNWANTED_GROUP"
            val all = configuration(fixture, null, ZigRunKind.TEST_FILE)
            val allOutput = execute(all)
            (allOutput.exitCode != 0) shouldBe true
            (allOutput.stdout + allOutput.stderr) shouldContain "UNWANTED_SINGLE"
        }, "zig test execution")
    }
})

private fun configuration(fixture: CodeInsightTestFixture, offset: Int?, kind: ZigRunKind): ZigRunConfiguration =
    ReadAction.computeBlocking<ZigRunConfiguration, RuntimeException> {
        val element = offset?.let { fixture.file.findElementAt(it)!! } ?: fixture.file
        ConfigurationContext(element).configurationsFromContext.orEmpty().map { it.configuration }
            .filterIsInstance<ZigRunConfiguration>().single { it.kind == kind }
    }

private fun hasMarker(fixture: CodeInsightTestFixture, offset: Int): Boolean =
    ReadAction.computeBlocking<Boolean, RuntimeException> {
        val element = fixture.file.findElementAt(offset)!!
        RunLineMarkerContributor.EXTENSION.allForLanguage(ZigLanguage).any { it.getInfo(element) != null }
    }

private fun execute(configuration: ZigRunConfiguration): ProcessOutput =
    PlatformTestUtil.waitForFuture(CompletableFuture.supplyAsync {
        CapturingProcessHandler(ZigRunCommandLineState.createCommandLine(configuration)).runProcess(30_000, true)
    }, 45_000).also { it.isTimeout shouldBe false }
