package com.zigrawr.jbplugin.declaration

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import io.kotest.matchers.shouldBe

class ZigCaptureBindingsSpec : IdeaHarness({
    should("navigate and highlight catch captures without mixing shadowed variables") {
        runSelectionFixture({ fixture ->
            val source = """
                fn run(output: anyerror![]u8) void {
                    const err = 1;
                    _ = err + 1;
                    _ = output catch |err| {
                        _ = err;
                        { const err = 2; _ = err + 2; }
                        return;
                    };
                    _ = output catch |other| { _ = other; return; };
                    _ = err + 3;
                }
            """.trimIndent()
            fixture.configureByText("catch-captures.zig", source)
            checkCapture(fixture, source, "err", "catch |err|", "_ = err;")
            checkCapture(fixture, source, "other", "catch |other|", "_ = other;")
            checkCapture(fixture, source, "err", "const err = 1", "err + 1", "err + 3")
            checkCapture(fixture, source, "err", "const err = 2", "err + 2")
        }, "zig error capture bindings")
    }

    should("scope if for and while captures to their branches and loop bodies") {
        runSelectionFixture({ fixture ->
            val source = """
                fn run(optional: ?u32, output: anyerror!u32, items: []u32) void {
                    const value = 1;
                    if (optional) |value| { _ = value + 2; } else { _ = value + 3; }
                    if (output) |number| { _ = number; } else |failure| { _ = failure; }
                    for (items, 0..) |*item, index| { _ = item; _ = index; }
                    for (items) |item| _ = item + 4;
                    while (optional) |value| : (_ = value + 5) { _ = value + 6; } else { _ = value + 7; }
                    while (output) |number| { _ = number + 8; } else |failure| { _ = failure + 9; }
                    _ = item;
                    _ = index;
                    _ = number;
                    _ = failure;
                }
            """.trimIndent()
            fixture.configureByText("branch-captures.zig", source)
            checkCapture(fixture, source, "value", "if (optional) |value|", "value + 2")
            checkCapture(fixture, source, "number", "if (output) |number|", "_ = number;")
            checkCapture(fixture, source, "failure", "else |failure| { _ = failure;", "_ = failure;")
            checkCapture(fixture, source, "item", "|*item, index|", "_ = item;")
            checkCapture(fixture, source, "index", "|*item, index|", "_ = index;")
            checkCapture(fixture, source, "item", "|item| _ = item + 4", "item + 4")
            checkCapture(fixture, source, "value", "while (optional) |value|", "value + 5", "value + 6")
            checkCapture(fixture, source, "number", "while (output) |number|", "number + 8")
            checkCapture(fixture, source, "failure", "else |failure| { _ = failure + 9", "failure + 9")
            checkCapture(fixture, source, "value", "const value = 1", "value + 3", "value + 7")
            for (name in listOf("item", "index", "number", "failure")) {
                val offset = source.lastIndexOf("_ = $name;") + "_ = ".length
                fixture.editor.caretModel.moveToOffset(offset)
                GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset).size shouldBe 0
            }
        }, "zig branch capture bindings")
    }
})

private fun checkCapture(fixture: CodeInsightTestFixture, source: String, name: String, declaration: String, vararg usages: String) {
    val declarationRange = captureRange(source, declaration, name)
    val expected = (listOf(declarationRange) + usages.map { captureRange(source, it, name) }).sortedBy { it.startOffset }
    for (range in expected) {
        val offset = range.startOffset
        fixture.editor.caretModel.moveToOffset(offset)
        PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
        val target = GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset).single()
        target.textOffset shouldBe declarationRange.startOffset
        target.text shouldBe name
        captureHighlights(fixture, offset) shouldBe expected
    }
}

private fun captureRange(source: String, context: String, name: String): TextRange {
    val start = source.indexOf(context)
    check(start >= 0 && context.indexOf(name) >= 0)
    return TextRange.from(start + context.indexOf(name), name.length)
}

private fun captureHighlights(fixture: CodeInsightTestFixture, offset: Int): List<TextRange> {
    val manager = HighlightManager.getInstance(fixture.project)
    for (highlight in fixture.editor.markupModel.allHighlighters) manager.removeSegmentHighlighter(fixture.editor, highlight)
    fixture.editor.caretModel.moveToOffset(offset)
    HighlightUsagesHandler.invoke(fixture.project, fixture.editor, fixture.file)
    val highlights = fixture.editor.markupModel.allHighlighters.filter {
        it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES ||
            it.textAttributesKey == EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
    }
    highlights.all { it.getTextAttributes(fixture.editor.colorsScheme)?.backgroundColor != null } shouldBe true
    return highlights.map { TextRange(it.startOffset, it.endOffset) }.sortedBy { it.startOffset }
}
