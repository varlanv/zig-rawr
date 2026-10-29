package com.zigrawr.jbplugin.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.foreign.model.Ast

class ZigLexer(state: IdeProcessState) : LexerBase() {

    private val bindings = state.bindings
    private var source: CharSequence = ""
    private var bufferLimit = 0
    private var snapshot: Ast? = null
    private var classifiedTypes = emptyArray<IElementType?>()
    private var nativeTokenIndex = 0
    private var cursor = 0
    private var currentType: IElementType? = null
    private var currentStart = 0
    private var currentEnd = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        require(startOffset in 0..endOffset && endOffset <= buffer.length)
        source = buffer
        bufferLimit = endOffset
        nativeTokenIndex = 0
        snapshot = if (startOffset == endOffset) null else {
            Ast(bindings.parseChars(buffer.subSequence(0, endOffset), ParseMode.Zig))
        }
        classifiedTypes = snapshot?.classifyTokens() ?: emptyArray()
        cursor = if (snapshot == null) endOffset else 0
        locateNextToken()
        while (currentType != null && currentEnd <= startOffset) {
            locateNextToken()
        }
        currentStart = maxOf(currentStart, startOffset)
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = currentType

    override fun getTokenStart(): Int = currentStart

    override fun getTokenEnd(): Int = currentEnd

    override fun advance() {
        locateNextToken()
    }

    override fun getBufferSequence(): CharSequence = source

    override fun getBufferEnd(): Int = bufferLimit

    private fun locateNextToken() {
        currentType = null
        currentStart = cursor
        currentEnd = cursor
        if (cursor >= bufferLimit) return

        val ast = snapshot ?: return
        while (nativeTokenIndex < ast.tokenCount()) {
            val positions = ast.tokenPositions(nativeTokenIndex)
            if (positions.end <= cursor) {
                nativeTokenIndex++
                continue
            }
            if (cursor < positions.start) {
                locateGapToken(positions.start)
                return
            }
            currentType = classifiedTypes[nativeTokenIndex] ?: ZigTokenTypes.forTag(ast.tokenTag(nativeTokenIndex))
            currentEnd = positions.end
            cursor = currentEnd
            nativeTokenIndex++
            return
        }
        locateGapToken(bufferLimit)
    }

    private fun locateGapToken(gapEnd: Int) {
        when {
            isZigWhitespace(source[cursor]) -> {
                currentType = TokenType.WHITE_SPACE
                currentEnd = cursor + 1
                while (currentEnd < gapEnd && isZigWhitespace(source[currentEnd])) currentEnd++
            }

            source[cursor] == '/' && cursor + 1 < gapEnd && source[cursor + 1] == '/' -> {
                currentType = ZigTokenTypes.LINE_COMMENT
                currentEnd = cursor + 2
                while (currentEnd < gapEnd && source[currentEnd] != '\r' && source[currentEnd] != '\n') currentEnd++
            }

            cursor == 0 && source[cursor] == '\uFEFF' -> {
                currentType = TokenType.WHITE_SPACE
                currentEnd = cursor + 1
            }

            else -> {
                currentType = TokenType.BAD_CHARACTER
                currentEnd = cursor + 1
            }
        }
        cursor = currentEnd
    }

    private fun isZigWhitespace(char: Char): Boolean = char == ' ' || char == '\t' || char == '\r' || char == '\n'
}
