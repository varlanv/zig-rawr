package com.zigrawr.zig.foreign

import com.zigrawr.lang.bin.asReadonly
import com.zigrawr.lang.log.Log
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.foreign.model.TokenTag
import com.zigrawr.zig.foreign.model.Tokens
import com.zigrawr.zig.tokenizer.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class TokenBindingsSpec : BaseSpec({
    val handles = context(Log.stderr) { BundledBindingsLoader.load() }

    should("read UTF-16 token ranges and retain snapshots after reuse and close") {
        val bindings = Bindings(handles)
        val source = "const x = \"π€😀\"; // trailing π😀"
        val tokens = try {
            val result = Tokens(bindings.tokens.parseChars(source))
            // Both entry points share the same native session.
            bindings.ast.parseChars("const = ;", ParseMode.Zig)
            bindings.tokens.parseChars(" ".repeat(1000))
            result
        } finally {
            bindings.close()
        }

        tokens.sourceLength() shouldBe source.length
        val ranges = mutableListOf<Pair<Int, Int>>()
        tokens.forEachToken { index, tag, positions ->
            tag shouldBe tokens.tokenTag(index)
            positions shouldBe tokens.tokenPositions(index)
            ranges.add(positions.start to positions.end)
        }
        ranges shouldBe listOf(0 to 5, 6 to 7, 8 to 9, 10 to 16, 16 to 17, source.length to source.length)
        tokens.tokenTag(0) shouldBe TokenTag.KEYWORD_CONST
        tokens.tokenTag(tokens.tokenCount() - 1) shouldBe TokenTag.EOF
        shouldThrow<IndexOutOfBoundsException> { tokens.tokenPositions(-1) }
        shouldThrow<IndexOutOfBoundsException> { tokens.tokenTag(tokens.tokenCount()) }
    }

    should("encode only the requested character range") {
        val text = "ignored 😀\nconst value = \"π😀\";\nignored suffix"
        val start = text.indexOf("const")
        val end = text.lastIndexOf('\n')
        val source = object : CharSequence {
            override val length = text.length
            override fun get(index: Int): Char {
                check(index in start until end)
                return text[index]
            }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("Unexpected copy")
        }
        val bindings = Bindings(handles)
        try {
            val tokens = Tokens(bindings.tokens.parseChars(source, start, end))
            tokens.sourceLength() shouldBe end - start
            tokens.tokenPositions(0).start shouldBe 0
            tokens.tokenTag(0) shouldBe TokenTag.KEYWORD_CONST
            val expected = Tokens(bindings.tokens.parseBytes(text.substring(start, end).toByteArray().asReadonly()))
            List(tokens.tokenCount()) { tokens.tokenPositions(it) } shouldBe
                List(expected.tokenCount()) { expected.tokenPositions(it) }
        } finally {
            bindings.close()
        }
    }

    should("handle empty input and recover after invalid UTF-8") {
        val bindings = Bindings(handles)
        try {
            shouldThrow<IllegalStateException> {
                bindings.tokens.parseBytes(byteArrayOf(0x80.toByte()).asReadonly())
            }
            val tokens = Tokens(bindings.tokens.parseChars(""))
            tokens.sourceLength() shouldBe 0
            tokens.tokenCount() shouldBe 1
            tokens.tokenTag(0) shouldBe TokenTag.EOF
            tokens.tokenPositions(0).start shouldBe 0
            tokens.tokenPositions(0).end shouldBe 0
        } finally {
            bindings.close()
        }
    }
})
