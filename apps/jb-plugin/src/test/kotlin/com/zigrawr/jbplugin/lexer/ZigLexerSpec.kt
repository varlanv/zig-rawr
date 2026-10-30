package com.zigrawr.jbplugin.lexer

import com.intellij.psi.tree.IElementType
import com.zigrawr.lang.log.Log
import com.zigrawr.zig.foreign.Bindings
import com.zigrawr.zig.foreign.BundledBindingsLoader
import com.zigrawr.zig.tokenizer.BaseSpec
import io.kotest.matchers.shouldBe

class ZigLexerSpec : BaseSpec({
    val handles = context(Log.stderr) { BundledBindingsLoader.load() }

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
