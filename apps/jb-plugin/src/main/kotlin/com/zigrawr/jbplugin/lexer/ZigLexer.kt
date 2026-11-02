package com.zigrawr.jbplugin.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.zig.foreign.Bindings
import com.zigrawr.zig.foreign.model.Tokens

private const val SNAPSHOT_CHARS = 4096

class ZigLexer internal constructor(private val bindings: Bindings) : LexerBase() {

    constructor(state: IdeProcessState) : this(state.bindings)

    private var source: CharSequence = ""
    private var bufferLimit = 0
    private var snapshot: Tokens? = null
    private var snapshotOffset = 0
    private var snapshotEnd = 0
    private var nativeTokenIndex = 0
    private var cursor = 0
    private var currentType: IElementType? = null
    private var currentStart = 0
    private var currentEnd = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        require(startOffset in 0..endOffset && endOffset <= buffer.length)
        source = buffer
        bufferLimit = endOffset
        snapshot = null
        cursor = startOffset
        if (startOffset < endOffset) {
            loadSnapshot()
            cursor = snapshotOffset
        }
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

    private fun loadSnapshot() {
        snapshotOffset = cursor
        while (snapshotOffset > 0 && source[snapshotOffset - 1] != '\n') snapshotOffset--
        if (snapshotOffset > 0) snapshotOffset--
        snapshotEnd = snapshotOffset + minOf(SNAPSHOT_CHARS, bufferLimit - snapshotOffset)
        while (snapshotEnd < bufferLimit && source[snapshotEnd - 1] != '\n') snapshotEnd++
        snapshot = Tokens(bindings.tokens.parseChars(source, snapshotOffset, snapshotEnd))
        nativeTokenIndex = 0
    }

    private fun locateNextToken() {
        currentType = null
        currentStart = cursor
        currentEnd = cursor
        if (cursor >= bufferLimit) return

        if (cursor >= snapshotEnd) loadSnapshot()
        val tokens = snapshot ?: return
        while (nativeTokenIndex < tokens.tokenCount()) {
            val positions = tokens.tokenPositions(nativeTokenIndex)
            val tokenStart = snapshotOffset + positions.start
            val tokenEnd = snapshotOffset + positions.end
            if (tokenEnd <= cursor) {
                nativeTokenIndex++
                continue
            }
            if (cursor < tokenStart) {
                locateGapToken(tokenStart)
                return
            }
            currentType = ZigTokenTypes.forTag(tokens.tokenTag(nativeTokenIndex))
            currentEnd = tokenEnd
            cursor = currentEnd
            nativeTokenIndex++
            return
        }
        locateGapToken(snapshotEnd)
    }

    private fun locateGapToken(gapEnd: Int) {
        when {
            isZigWhitespace(source[cursor]) -> {
                currentType = TokenType.WHITE_SPACE
                currentEnd = cursor + 1
                while (currentEnd < bufferLimit && isZigWhitespace(source[currentEnd])) currentEnd++
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
