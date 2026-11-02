package com.zigrawr.jbplugin.highlight

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import io.kotest.matchers.shouldBe

class ZigIdentifierHighlightingSpec : IdeaHarness({
    should("highlight matching declarations with standard backgrounds after caret moves and edits") {
        runSelectionFixture({ fixture ->
            val source = """
                const value = 1;
                fn helper(value: u32) u32 { return value; }
                fn run() void {
                    _ = value + 1;
                    { const value = 2; _ = value + 3; }
                    _ = value + 4;
                    _ = helper(1);
                    _ = helper(2);
                    _ = "value";
                    // value
                }
            """.trimIndent()
            fixture.configureByText("identifier-highlighting.zig", source)
            val global = source.indexOf("value") + 1
            val globalRanges = expectedRanges(source, "value", "const value = 1", "value + 1", "value + 4")
            highlightedRanges(fixture, global) shouldBe globalRanges
            highlightedRanges(fixture, source.indexOf("value + 3") + 1) shouldBe
                expectedRanges(source, "value", "const value = 2", "value + 3")
            highlightedRanges(fixture, source.indexOf("helper(1)") + 1) shouldBe
                expectedRanges(source, "helper", "fn helper", "helper(1)", "helper(2)")
            highlightedRanges(fixture, source.indexOf("fn run")) shouldBe emptyList()
            highlightedRanges(fixture, source.indexOf("// value") + 4) shouldBe emptyList()
            highlightedRanges(fixture, global) shouldBe globalRanges

            val edited = source.indexOf("value + 4")
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                fixture.editor.document.replaceString(edited, edited + "value".length, "other")
            }
            highlightedRanges(fixture, global) shouldBe globalRanges.dropLast(1)
            highlightedRanges(fixture, edited + 1) shouldBe emptyList()
        }, "zig identifier backgrounds")
    }

    should("highlight the same imported function through aliases and direct imports") {
        runSelectionFixture({ fixture ->
            fixture.addFileToProject("lib/math.zig", "pub fn add(a: u32, b: u32) u32 { return a + b; }")
            fixture.addFileToProject("lib/other.zig", "pub fn add(a: u32, b: u32) u32 { return a - b; }")
            val source = """
                const math = @import("lib/math.zig");
                const alias = math;
                const other = @import("lib/other.zig");
                fn run() void {
                    _ = math.add(1, 2);
                    _ = alias.add(3, 4);
                    _ = @import("lib/math.zig").add(5, 6);
                    _ = other.add(7, 8);
                    _ = "add";
                    // add
                }
            """.trimIndent()
            val main = fixture.addFileToProject("main.zig", source)
            fixture.configureFromExistingVirtualFile(main.virtualFile)
            highlightedRanges(fixture, source.indexOf("add(3, 4)") + 1) shouldBe
                expectedRanges(source, "add", "add(1, 2)", "add(3, 4)", "add(5, 6)")
            highlightedRanges(fixture, source.indexOf("\"add\"") + 2) shouldBe emptyList()
        }, "zig imported identifier backgrounds")
    }
})

private fun highlightedRanges(fixture: CodeInsightTestFixture, offset: Int): List<TextRange> {
    val manager = HighlightManager.getInstance(fixture.project)
    for (highlight in fixture.editor.markupModel.allHighlighters) {
        manager.removeSegmentHighlighter(fixture.editor, highlight)
    }
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    HighlightUsagesHandler.invoke(fixture.project, fixture.editor, fixture.file)
    val highlights = fixture.editor.markupModel.allHighlighters.filter {
        it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES ||
            it.textAttributesKey == EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
    }
    highlights.all { it.getTextAttributes(fixture.editor.colorsScheme)?.backgroundColor != null } shouldBe true
    return highlights.map { TextRange(it.startOffset, it.endOffset) }.sortedBy { it.startOffset }
}

private fun expectedRanges(source: String, name: String, vararg contexts: String): List<TextRange> =
    contexts.map { context ->
        val start = source.indexOf(context)
        check(start >= 0)
        TextRange.from(start + context.indexOf(name), name.length)
    }.sortedBy { it.startOffset }
