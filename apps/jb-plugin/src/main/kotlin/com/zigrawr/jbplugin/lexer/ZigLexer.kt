package com.zigrawr.jbplugin.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.zig.foreign.Bindings
import com.zigrawr.zig.foreign.model.Tokens

class ZigLexer internal constructor(private val bindings: Bindings) : LexerBase() {

    constructor(state: IdeProcessState) : this(state.bindings)

    private var source: CharSequence = ""
    private var bufferLimit = 0
    private var snapshot: Tokens? = null
    private var snapshotOffset = 0
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
        // todo needs proper testing
        // a physical line is an independent lexical unit in Zig. Rewind within
        // this line so starts inside strings, comments and UTF-16 pairs are safe.
        snapshotOffset = startOffset
        while (snapshotOffset > 0 && buffer[snapshotOffset - 1] != '\n') snapshotOffset--
        // include the preceding LF so an interior U+FEFF cannot become a slice BOM.
        if (snapshotOffset > 0) snapshotOffset--
        snapshot = if (startOffset == endOffset) null else {
            // todo - need to check whether zig integration efficiently works with partial reads
            //        additionally - when run with debugger, I see this function being called on each input character ~4-5 times per input character. For big files that means
            //        potentially 100k+ chars scan for each input character. Need to investigate whether there it is expected or something causes rescans; perhaps also some sort of debounce is needed ?
            Tokens(bindings.tokens.parseChars(buffer, snapshotOffset, endOffset))
        }
        cursor = if (snapshot == null) endOffset else snapshotOffset
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
