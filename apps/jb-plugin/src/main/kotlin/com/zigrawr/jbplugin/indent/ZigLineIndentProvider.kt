package com.zigrawr.jbplugin.indent

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.file.ZigLanguage
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.zig.foreign.model.TokenTag

private val openingBrace = ZigTokenTypes.forTag(TokenTag.L_BRACE)
private val closingBrace = ZigTokenTypes.forTag(TokenTag.R_BRACE)
private val openingParenthesis = ZigTokenTypes.forTag(TokenTag.L_PAREN)
private val closingParenthesis = ZigTokenTypes.forTag(TokenTag.R_PAREN)
private val openingBracket = ZigTokenTypes.forTag(TokenTag.L_BRACKET)
private val closingBracket = ZigTokenTypes.forTag(TokenTag.R_BRACKET)

class ZigLineIndentProvider : LineIndentProvider {

    override fun getLineIndent(
        project: Project,
        editor: Editor,
        language: Language?,
        offset: Int
    ): String? {
        if (!isSuitableFor(language)) return null
        val document = editor.document
        if (offset !in 0..document.textLength) return null
        val options = CodeStyle.getIndentOptions(project, document)
        val line = document.getLineNumber(offset)
        val opening = findEnclosingDelimiter(editor, offset)
            ?: return document.lineIndent(maxOf(0, line - 1))
        val baseIndent = document.lineIndent(document.getLineNumber(opening.offset))
        val lineEnd = document.getLineEndOffset(line)
        val text = document.charsSequence
        var nextOffset = offset
        while (nextOffset < lineEnd && (text[nextOffset] == ' ' || text[nextOffset] == '\t')) nextOffset++
        val closing = if (nextOffset < lineEnd) editor.highlighter.createIterator(nextOffset) else null
        if (closing != null && !closing.atEnd() && matchingOpening(closing.tokenType) == opening.type) {
            return baseIndent
        }

        val indentSize = if (opening.type == openingBrace) options.INDENT_SIZE else options.CONTINUATION_INDENT_SIZE
        if (!options.USE_TAB_CHARACTER || options.SMART_TABS && opening.type != openingBrace) {
            return baseIndent + " ".repeat(indentSize)
        }
        val tabSize = maxOf(1, options.TAB_SIZE)
        var column = 0
        for (char in baseIndent) {
            column = if (char == '\t') column + tabSize - column % tabSize else column + 1
        }
        column += indentSize
        return "\t".repeat(column / tabSize) + " ".repeat(column % tabSize)
    }

    override fun isSuitableFor(language: Language?): Boolean = language == ZigLanguage

    private fun findEnclosingDelimiter(editor: Editor, offset: Int): OpeningDelimiter? {
        if (offset == 0) return null
        val iterator = editor.highlighter.createIterator(offset - 1)
        val expectedOpenings = ArrayDeque<IElementType>()
        while (!iterator.atEnd()) {
            val type = iterator.tokenType
            val expectedOpening = matchingOpening(type)
            if (expectedOpening != null) {
                expectedOpenings.addLast(expectedOpening)
            } else if (type == openingBrace || type == openingParenthesis || type == openingBracket) {
                if (expectedOpenings.isEmpty()) return OpeningDelimiter(type, iterator.start)
                if (expectedOpenings.removeLast() != type) return null
            }
            iterator.retreat()
        }
        return null
    }
}

private data class OpeningDelimiter(val type: IElementType, val offset: Int)

private fun matchingOpening(type: IElementType): IElementType? = when (type) {
    closingBrace -> openingBrace
    closingParenthesis -> openingParenthesis
    closingBracket -> openingBracket
    else -> null
}

private fun Document.lineIndent(line: Int): String {
    val text = charsSequence
    val start = getLineStartOffset(line)
    val end = getLineEndOffset(line)
    var offset = start
    while (offset < end && (text[offset] == ' ' || text[offset] == '\t')) offset++
    return text.subSequence(start, offset).toString()
}
