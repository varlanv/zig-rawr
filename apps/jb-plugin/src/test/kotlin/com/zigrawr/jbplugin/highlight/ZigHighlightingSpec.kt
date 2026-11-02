package com.zigrawr.jbplugin.highlight

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.zig.foreign.model.TokenTag
import io.kotest.matchers.shouldBe

class ZigHighlightingSpec : IdeaHarness({
    should("keep incremental highlighting consistent after editing a large Zig file") {
        runSelectionFixture({ fixture ->
            val source = "const text = <caret>value; // π😀\r\n" +
                "const number = 123;\n".repeat(8192) +
                "const tail = \"finished\";"
            fixture.configureByText("highlighting.zig", source)
            val editor = fixture.editor as EditorEx
            val full = LexerEditorHighlighter(ZigSyntaxHighlighter(), EditorColorsManager.getInstance().globalScheme)

            fun checkHighlighting() {
                full.setText(editor.document.immutableCharSequence)
                readHighlighting(editor.highlighter) shouldBe readHighlighting(full)
            }

            checkHighlighting()
            fixture.type('"')
            checkHighlighting()
            editor.highlighter.createIterator(editor.caretModel.offset).tokenType shouldBe
                ZigTokenTypes.forTag(TokenTag.INVALID)

            fixture.performEditorAction("EditorBackSpace")
            checkHighlighting()
            editor.highlighter.createIterator(editor.caretModel.offset).tokenType shouldBe
                ZigTokenTypes.forTag(TokenTag.IDENTIFIER)

            editor.caretModel.moveToOffset(editor.document.textLength - 2)
            fixture.type("😀")
            checkHighlighting()
        }, "zig highlighting")
    }
})

private data class HighlightedToken(val type: IElementType?, val start: Int, val end: Int)

private fun readHighlighting(highlighter: EditorHighlighter): List<HighlightedToken> {
    val tokens = ArrayList<HighlightedToken>()
    val iterator = highlighter.createIterator(0)
    while (!iterator.atEnd()) {
        tokens.add(HighlightedToken(iterator.tokenType, iterator.start, iterator.end))
        iterator.advance()
    }
    return tokens
}
