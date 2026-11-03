package com.zigrawr.jbplugin.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.zigrawr.lang.log.Log
import com.zigrawr.zig.foreign.Bindings
import com.zigrawr.zig.foreign.BundledBindingsLoader
import com.zigrawr.zig.foreign.model.TokenTag
import com.zigrawr.zig.foreign.model.Tokens
import com.zigrawr.zig.tokenizer.BaseSpec
import io.kotest.matchers.shouldBe
import java.lang.foreign.Arena

class ZigLexerSpec : BaseSpec({
    val arena = Arena.ofShared()
    afterSpec { arena.close() }
    val handles = context(Log.stderr) { BundledBindingsLoader.load(arena) }

    should("match full lexing when restarted inside tokens and UTF-16 pairs") {
        val bindings = Bindings(handles)
        try {
            val lexer = ZigLexer(bindings)
            val source = "\uFEFFconst value = \"π😀\"; // comment 😀\r\n" +
                "/// docs\nconst text = \\\\line 😀\n    \\\\more\n;\n" +
                "\uFEFFinvalid interior BOM\nconst unfinished = \"π😀"
            // A bounded end also exercises truncated-input semantics.
            for (end in listOf(source.length, source.indexOf("comment") + 3)) {
                val complete = readTokens(lexer, source, 0, end)
                for (start in 0..end) {
                    val expected = complete.filter { it.end > start }.map { it.copy(start = maxOf(it.start, start)) }
                    readTokens(lexer, source, start, end) shouldBe expected
                }
            }

            val largeSource = source + "\n" + (" \t\r\n".repeat(2048)) +
                "const long_line = \"" + "π😀".repeat(4096) + "\";\r\n" +
                (source + "\n").repeat(128) + "const last = 42; // final comment"
            for (end in listOf(4095, 4096, 4097, 8192, largeSource.length - 2, largeSource.length)) {
                val complete = readTokens(lexer, largeSource, 0, end)
                val native = Tokens(bindings.tokens.parseChars(largeSource, 0, end))
                val expectedNative = ArrayList<LexedToken>()
                native.forEachToken { _, tag, span ->
                    if (tag != TokenTag.EOF) {
                        expectedNative.add(LexedToken(ZigTokenTypes.forTag(tag), span.start, span.end))
                    }
                }
                complete.filter { it.type != TokenType.WHITE_SPACE && it.type != ZigTokenTypes.LINE_COMMENT } shouldBe
                    expectedNative
                for (start in listOf(0, 1, 4094, 4095, 4096, 8191, end - 1, end).filter { it <= end }) {
                    val expected = complete.filter { it.end > start }.map { it.copy(start = maxOf(it.start, start)) }
                    readTokens(lexer, largeSource, start, end) shouldBe expected
                }
            }
        } finally {
            bindings.close()
        }
    }
})

private data class LexedToken(val type: IElementType, val start: Int, val end: Int)

private fun readTokens(lexer: ZigLexer, source: CharSequence, start: Int, end: Int): List<LexedToken> {
    lexer.start(source, start, end, 0)
    lexer.bufferSequence shouldBe source
    lexer.bufferEnd shouldBe end
    val result = mutableListOf<LexedToken>()
    var cursor = start
    while (true) {
        val type = lexer.tokenType ?: break
        lexer.tokenStart shouldBe cursor
        check(lexer.tokenEnd > cursor && lexer.tokenEnd <= end)
        result.add(LexedToken(type, lexer.tokenStart, lexer.tokenEnd))
        cursor = lexer.tokenEnd
        lexer.advance()
    }
    cursor shouldBe end
    return result
}
