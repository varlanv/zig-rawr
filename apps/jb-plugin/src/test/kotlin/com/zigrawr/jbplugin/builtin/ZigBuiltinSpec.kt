package com.zigrawr.jbplugin.builtin

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.documentation.ZigDocumentationProvider
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.jbplugin.toolchain.ZigToolchainSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path

class ZigBuiltinSpec : IdeaHarness({
    should("document and navigate selected-toolchain builtins and highlight matching builtin tokens") {
        runSelectionFixture({ fixture ->
            val info =
                PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            val source = """
                const intCast = 4;
                fn run(value: u32) void {
                    const first: u8 = @intCast(value);
                    const second: u16 = @intCast(value);
                    _ = @bitCast(value);
                    _ = @TypeOf(value);
                    _ = intCast;
                    _ = "@intCast";
                    // @intCast
                    _ = @notAnExistingBuiltin(value);
                }
            """.trimIndent()
            fixture.configureByText("builtins.zig", source)
            val offset = source.indexOf("@intCast(value)")
            val expected =
                listOf(TextRange.from(offset, 8), TextRange.from(source.indexOf("@intCast(value)", offset + 1), 8))
            highlightedRanges(fixture, offset) shouldBe expected
            highlightedRanges(fixture, offset + 3) shouldBe expected
            val target = documentationTargets(fixture, offset).single()
            val html = documentation(target)
            html shouldContain "@intCast"
            html shouldContain "Converts an integer to another integer"
            html shouldNotContain "<script"
            html shouldNotContain "Table of Contents"
            ReadAction.computeBlocking<String, RuntimeException> { target.computePresentation().locationText.orEmpty() } shouldContain info.version
            fixture.editor.caretModel.moveToOffset(offset)
            val navigation = GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset)
                .single() as ZigBuiltinPsiElement
            navigation.name shouldBe "@intCast"
            navigation.documentationUrl.endsWith("#intCast") shouldBe true
            navigation.documentationUrl shouldContain Path.of(info.executable).parent.resolve("doc/langref.html")
                .toUri().toString()
            documentation(
                documentationTargets(
                    fixture,
                    source.indexOf("@TypeOf(value)") + 2
                ).single()
            ) shouldContain "@TypeOf"
            val unknown = source.indexOf("@notAnExistingBuiltin")
            documentationTargets(fixture, unknown).size shouldBe 0
            fixture.editor.caretModel.moveToOffset(unknown)
            GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, unknown).size shouldBe 0
        }, "zig builtin integration")
    }

    should("refresh builtin targets after edits and discard documentation from an invalid toolchain") {
        runSelectionFixture({ fixture ->
            val service = fixture.project.service<ZigToolchainService>()
            PlatformTestUtil.waitForFuture(service.refresh(), 15_000)!!
            fixture.configureByText("builtin-edits.zig", "const value: u8 = @intCast(12);")
            val target = documentationTargets(fixture, fixture.editor.document.text.indexOf("@intCast")).single()
            val before = documentation(target)
            val pointer =
                ReadAction.computeBlocking<com.intellij.model.Pointer<out DocumentationTarget>, RuntimeException> { target.createPointer() }
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                fixture.editor.document.insertString(
                    0,
                    "const header = 1;\n"
                )
            }
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
            documentation(pointer.dereference()!!) shouldBe before
            val offset = fixture.editor.document.text.indexOf("@intCast")
            documentation(documentationTargets(fixture, offset + 3).single()) shouldBe before
            fixture.project.service<ZigToolchainSettings>().executable =
                Path.of(fixture.tempDirFixture.tempDirPath, "missing-zig").toString()
            PlatformTestUtil.waitForFuture(service.refresh(), 15_000) shouldBe null
            documentationTargets(fixture, offset).size shouldBe 0
            fixture.editor.caretModel.moveToOffset(offset)
            GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset).size shouldBe 0
            highlightedRanges(fixture, offset) shouldBe listOf(TextRange.from(offset, 8))
        }, "zig builtin refresh")
    }
})

private fun documentationTargets(fixture: CodeInsightTestFixture, offset: Int): List<DocumentationTarget> =
    ReadAction.computeBlocking<List<DocumentationTarget>, RuntimeException> {
        DocumentationTargetProvider.EP_NAME.extensionList.single { it is ZigDocumentationProvider }
            .documentationTargets(fixture.file, offset)
    }

private fun documentation(target: DocumentationTarget): String = ReadAction.computeBlocking<String, RuntimeException> {
    (target.computeDocumentation() as DocumentationData).html
}

private fun highlightedRanges(fixture: CodeInsightTestFixture, offset: Int): List<TextRange> {
    val manager = HighlightManager.getInstance(fixture.project)
    fixture.editor.markupModel.allHighlighters.forEach { manager.removeSegmentHighlighter(fixture.editor, it) }
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    HighlightUsagesHandler.invoke(fixture.project, fixture.editor, fixture.file)
    val highlights = fixture.editor.markupModel.allHighlighters.filter {
        it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES || it.textAttributesKey == EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
    }
    highlights.all { it.getTextAttributes(fixture.editor.colorsScheme)?.backgroundColor != null } shouldBe true
    return highlights.map { TextRange(it.startOffset, it.endOffset) }.sortedBy { it.startOffset }
}
