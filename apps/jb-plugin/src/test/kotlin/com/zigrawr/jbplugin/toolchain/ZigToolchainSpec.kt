package com.zigrawr.jbplugin.toolchain

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.documentation.ZigDocumentationProvider
import com.zigrawr.jbplugin.runSelectionFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class ZigToolchainSpec : IdeaHarness({
    should("discover the installed Zig and navigate its standard library with documentation") {
        runSelectionFixture({ fixture ->
            val settings = fixture.project.service<ZigToolchainSettings>()
            val service = fixture.project.service<ZigToolchainService>()
            settings.executable = ""
            val info = PlatformTestUtil.waitForFuture(service.refresh(), 15_000)!!
            info.version.isNotBlank() shouldBe true
            val standardLibrary = Path.of(info.stdDirectory)
            Files.isRegularFile(standardLibrary.resolve("std.zig")) shouldBe true
            Files.isRegularFile(standardLibrary.resolve("mem.zig")) shouldBe true
            service.currentInfo shouldBe info
            PlatformTestUtil.waitForFuture(service.inspect("zig"), 15_000).stdDirectory shouldBe info.stdDirectory

            fixture.configureByText(
                "standard-library.zig", """
                const std = @import("std");
                const bytes = std.mem;
                fn main() void {
                    _ = std.mem.eql(u8, "", "");
                    _ = bytes.eql(u8, "a", "b");
                    _ = @import("std").mem.eql(u8, "c", "d");
                }
            """.trimIndent()
            )
            val root = targets(fixture, "@import(\"std\")", "std").single()
            root.containingFile.virtualFile.toNioPath().toRealPath() shouldBe
                standardLibrary.resolve("std.zig").toRealPath()
            targets(fixture, "std.mem;", "mem").single().text shouldBe "mem"
            for (context in listOf("std.mem.eql(", "bytes.eql(", ").mem.eql(")) {
                val target = targets(fixture, context, "eql").single()
                target.text shouldBe "eql"
                target.containingFile.virtualFile.toNioPath().toRealPath() shouldBe
                    standardLibrary.resolve("mem.zig").toRealPath()
                val documentation = documentation(fixture, context, "eql")
                documentation shouldContain "pub fn eql("
                documentation shouldContain "Returns true if and only if"
            }
        }, "zig standard library")
    }

    should("clear stale standard library navigation after selecting an invalid toolchain") {
        runSelectionFixture({ fixture ->
            val settings = fixture.project.service<ZigToolchainSettings>()
            val service = fixture.project.service<ZigToolchainService>()
            settings.executable = ""
            val active = PlatformTestUtil.waitForFuture(service.refresh(), 15_000)!!
            fixture.addFileToProject("helper.zig", "pub fn local() void {}")
            val main = fixture.addFileToProject(
                "main.zig", """
                const std = @import("std");
                const helper = @import("helper.zig");
                fn main() void { _ = std.mem.eql(u8, "", ""); helper.local(); }
            """.trimIndent()
            )
            fixture.configureFromExistingVirtualFile(main.virtualFile)
            targets(fixture, "std.mem.eql(", "eql").single().text shouldBe "eql"
            val invalid = Path.of(fixture.tempDirFixture.tempDirPath, "missing-zig").toString()
            runCatching { PlatformTestUtil.waitForFuture(service.inspect(invalid), 15_000) }.isFailure shouldBe true
            service.currentInfo shouldBe active
            settings.executable = invalid
            PlatformTestUtil.waitForFuture(service.refresh(), 15_000) shouldBe null
            service.currentInfo shouldBe null
            targets(fixture, "std.mem.eql(", "eql").size shouldBe 0
            targets(fixture, "helper.local();", "local").single().containingFile.name shouldBe "helper.zig"
        }, "zig toolchain changes")
    }
})

private fun targets(fixture: CodeInsightTestFixture, context: String, name: String): Array<PsiElement> {
    val offset = offset(fixture, context, name)
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    return GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset)
}

private fun documentation(fixture: CodeInsightTestFixture, context: String, name: String): String =
    ReadAction.computeBlocking<String, RuntimeException> {
        val provider = DocumentationTargetProvider.EP_NAME.extensionList.single { it is ZigDocumentationProvider }
        val target = provider.documentationTargets(fixture.file, offset(fixture, context, name)).single()
        (target.computeDocumentation() as DocumentationData).html
    }

private fun offset(fixture: CodeInsightTestFixture, context: String, name: String): Int {
    val start = fixture.editor.document.immutableCharSequence.indexOf(context)
    check(start >= 0 && context.indexOf(name) >= 0)
    return start + context.indexOf(name)
}
